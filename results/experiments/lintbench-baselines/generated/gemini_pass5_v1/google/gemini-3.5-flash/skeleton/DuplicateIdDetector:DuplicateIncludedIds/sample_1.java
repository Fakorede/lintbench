package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the id's need to be unique "
                            + "within any chain of included layouts, or Activity#findViewById() can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<File, LayoutInfo> layoutInfos = new HashMap<>();
    private final Set<String> reportedDuplicates = new HashSet<>();

    private static class LayoutInfo {
        final File file;
        final String layoutName;
        final List<IdOccurrence> ids = new ArrayList<>();
        final List<IncludeOccurrence> includes = new ArrayList<>();

        LayoutInfo(File file, String layoutName) {
            this.file = file;
            this.layoutName = layoutName;
        }
    }

    private static class IdOccurrence {
        final String id;
        final Location location;

        IdOccurrence(String id, Location location) {
            this.id = id;
            this.location = location;
        }
    }

    private static class IncludeOccurrence {
        final String layoutName;
        final Location location;

        IncludeOccurrence(String layoutName, Location location) {
            this.layoutName = layoutName;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("include");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutInfos.clear();
        reportedDuplicates.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Map<String, List<LayoutInfo>> layoutsByName = new HashMap<>();
        for (LayoutInfo info : layoutInfos.values()) {
            List<LayoutInfo> list = layoutsByName.get(info.layoutName);
            if (list == null) {
                list = new ArrayList<>();
                layoutsByName.put(info.layoutName, list);
            }
            list.add(info);
        }

        for (LayoutInfo root : layoutInfos.values()) {
            Set<LayoutInfo> transitives = getTransitiveIncludes(root, layoutsByName);

            Map<String, List<IdOccurrence>> idsMap = new HashMap<>();
            for (LayoutInfo info : transitives) {
                for (IdOccurrence occ : info.ids) {
                    List<IdOccurrence> list = idsMap.get(occ.id);
                    if (list == null) {
                        list = new ArrayList<>();
                        idsMap.put(occ.id, list);
                    }
                    list.add(occ);
                }
            }

            for (Map.Entry<String, List<IdOccurrence>> entry : idsMap.entrySet()) {
                String id = entry.getKey();
                List<IdOccurrence> occurrences = entry.getValue();
                if (occurrences.size() > 1) {
                    for (int i = 0; i < occurrences.size(); i++) {
                        for (int j = i + 1; j < occurrences.size(); j++) {
                            IdOccurrence occ1 = occurrences.get(i);
                            IdOccurrence occ2 = occurrences.get(j);

                            if (!occ1.location.getFile().equals(occ2.location.getFile())) {
                                String key = getDuplicateKey(id, occ1.location, occ2.location);
                                if (reportedDuplicates.add(key)) {
                                    reportDuplicate(context, id, occ1, occ2, root);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Set<LayoutInfo> getTransitiveIncludes(
            LayoutInfo root, Map<String, List<LayoutInfo>> layoutsByName) {
        Set<LayoutInfo> result = new HashSet<>();
        Queue<LayoutInfo> queue = new LinkedList<>();
        queue.add(root);
        result.add(root);

        while (!queue.isEmpty()) {
            LayoutInfo current = queue.poll();
            for (IncludeOccurrence include : current.includes) {
                List<LayoutInfo> includedLayouts = layoutsByName.get(include.layoutName);
                if (includedLayouts != null) {
                    for (LayoutInfo included : includedLayouts) {
                        if (result.add(included)) {
                            queue.add(included);
                        }
                    }
                }
            }
        }
        return result;
    }

    private String getDuplicateKey(String id, Location l1, Location l2) {
        String f1 = l1.getFile().getAbsolutePath();
        int start1 = l1.getStart() != null ? l1.getStart().getOffset() : 0;
        String f2 = l2.getFile().getAbsolutePath();
        int start2 = l2.getStart() != null ? l2.getStart().getOffset() : 0;

        if (f1.compareTo(f2) < 0 || (f1.equals(f2) && start1 < start2)) {
            return id + ":" + f1 + "@" + start1 + ":" + f2 + "@" + start2;
        } else {
            return id + ":" + f2 + "@" + start2 + ":" + f1 + "@" + start1;
        }
    }

    private void reportDuplicate(
            Context context, String id, IdOccurrence occ1, IdOccurrence occ2, LayoutInfo root) {
        String file1Name = occ1.location.getFile().getName();
        String file2Name = occ2.location.getFile().getName();

        String message =
                String.format(
                        "Duplicate id `@+id/%s` across layouts `%s` and `%s` combined with include tags in `%s`",
                        id, file1Name, file2Name, root.file.getName());

        Location primary = occ1.location;
        Location secondary = occ2.location;
        secondary.setMessage("Alternative definition here");
        primary.setSecondary(secondary);

        context.report(ISSUE, primary, message);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("include".equals(element.getTagName())) {
            String layoutAttr = element.getAttribute("layout");
            if (layoutAttr != null && !layoutAttr.isEmpty()) {
                String includedLayout = layoutAttr;
                if (includedLayout.startsWith("@layout/")) {
                    includedLayout = includedLayout.substring(8);
                }
                LayoutInfo info = getLayoutInfo(context);
                Attr layoutNode = element.getAttributeNode("layout");
                Location location =
                        layoutNode != null
                                ? context.getValueLocation(layoutNode)
                                : context.getNameLocation(element);
                info.includes.add(new IncludeOccurrence(includedLayout, location));
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        String name = attribute.getName();
        if ("id".equals(localName) || "android:id".equals(name)) {
            String idValue = attribute.getValue();
            String id = idValue;
            if (id.startsWith("@+id/")) {
                id = id.substring(5);
            } else if (id.startsWith("@id/")) {
                id = id.substring(4);
            }
            LayoutInfo info = getLayoutInfo(context);
            Location location = context.getValueLocation(attribute);
            info.ids.add(new IdOccurrence(id, location));
        }
    }

    private LayoutInfo getLayoutInfo(XmlContext context) {
        File file = context.file;
        LayoutInfo info = layoutInfos.get(file);
        if (info == null) {
            String fileName = file.getName();
            String layoutName = fileName;
            int dot = fileName.lastIndexOf('.');
            if (dot != -1) {
                layoutName = fileName.substring(0, dot);
            }
            info = new LayoutInfo(file, layoutName);
            layoutInfos.put(file, info);
        }
        return info;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(DuplicateIdDetector other) {
        return 0;
    }
}