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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
                            + "within any chain of included layouts, or `Activity#findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<IdOccurrence>> layoutToIds;
    private Map<String, List<IncludeOccurrence>> layoutToIncludes;

    private static class IdOccurrence {
        final String id;
        final Location location;
        final String layout;

        IdOccurrence(String id, Location location, String layout) {
            this.id = id;
            this.location = location;
            this.layout = layout;
        }
    }

    private static class IncludeOccurrence {
        final String includedLayout;
        final Location location;

        IncludeOccurrence(String includedLayout, Location location) {
            this.includedLayout = includedLayout;
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
        if (layoutToIds == null) {
            layoutToIds = new HashMap<>();
        }
        if (layoutToIncludes == null) {
            layoutToIncludes = new HashMap<>();
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutToIds = new HashMap<>();
        layoutToIncludes = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (layoutToIds == null || layoutToIncludes == null) {
            return;
        }

        Set<String> reportedConflicts = new HashSet<>();

        for (String rootLayout : layoutToIds.keySet()) {
            Map<String, List<IdOccurrence>> idToOccurrences = new HashMap<>();
            Set<String> visitedLayouts = new HashSet<>();
            collectIds(rootLayout, idToOccurrences, visitedLayouts);

            for (Map.Entry<String, List<IdOccurrence>> entry : idToOccurrences.entrySet()) {
                List<IdOccurrence> occurrences = entry.getValue();
                if (occurrences.size() > 1) {
                    for (int i = 0; i < occurrences.size(); i++) {
                        for (int j = i + 1; j < occurrences.size(); j++) {
                            IdOccurrence o1 = occurrences.get(i);
                            IdOccurrence o2 = occurrences.get(j);

                            String conflictKey = getConflictKey(o1, o2);
                            if (reportedConflicts.contains(conflictKey)) {
                                continue;
                            }
                            reportedConflicts.add(conflictKey);

                            String message = String.format(
                                    "Duplicate id '%s' in layouts '%s' and '%s' joined by include",
                                    o1.id, o1.layout, o2.layout);

                            Location secondary = o1.location;
                            secondary.setMessage("Also defined here");
                            o2.location.setSecondary(secondary);

                            context.report(ISSUE, o2.location, message);
                        }
                    }
                }
            }
        }
    }

    private void collectIds(String layout, Map<String, List<IdOccurrence>> idToOccurrences, Set<String> visited) {
        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        List<IdOccurrence> ids = layoutToIds.get(layout);
        if (ids != null) {
            for (IdOccurrence occ : ids) {
                List<IdOccurrence> list = idToOccurrences.get(occ.id);
                if (list == null) {
                    list = new ArrayList<>();
                    idToOccurrences.put(occ.id, list);
                }
                list.add(occ);
            }
        }

        List<IncludeOccurrence> includes = layoutToIncludes.get(layout);
        if (includes != null) {
            for (IncludeOccurrence inc : includes) {
                collectIds(inc.includedLayout, idToOccurrences, visited);
            }
        }
    }

    private String getConflictKey(IdOccurrence o1, IdOccurrence o2) {
        String path1 = o1.location.getFile().getPath();
        String path2 = o2.location.getFile().getPath();
        int line1 = o1.location.getStart() != null ? o1.location.getStart().getLine() : 0;
        int line2 = o2.location.getStart() != null ? o2.location.getStart().getLine() : 0;
        if (path1.compareTo(path2) < 0) {
            return o1.id + "|" + path1 + "|" + line1 + "|" + path2 + "|" + line2;
        } else {
            return o1.id + "|" + path2 + "|" + line2 + "|" + path1 + "|" + line1;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("include".equals(element.getTagName())) {
            String layoutVal = element.getAttributeNS("http://schemas.android.com/apk/res/android", "layout");
            if (layoutVal.isEmpty()) {
                layoutVal = element.getAttribute("android:layout");
            }
            if (layoutVal.startsWith("@layout/")) {
                String includedLayout = layoutVal.substring(8);
                String layout = getLayoutName(context);
                Location location = context.getLocation(element);

                List<IncludeOccurrence> list = layoutToIncludes.computeIfAbsent(layout, k -> new ArrayList<>());
                list.add(new IncludeOccurrence(includedLayout, location));
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String id = attribute.getValue();
        String idName;
        if (id.startsWith("@+id/")) {
            idName = id.substring(5);
        } else if (id.startsWith("@id/")) {
            idName = id.substring(4);
        } else {
            return;
        }

        String layout = getLayoutName(context);
        Location location = context.getLocation(attribute);

        List<IdOccurrence> list = layoutToIds.computeIfAbsent(layout, k -> new ArrayList<>());
        list.add(new IdOccurrence(idName, location, layout));
    }

    private String getLayoutName(XmlContext context) {
        String name = context.file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    @Override
    @NonNull
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(@NonNull DuplicateIdDetector other) {
        return 0;
    }
}