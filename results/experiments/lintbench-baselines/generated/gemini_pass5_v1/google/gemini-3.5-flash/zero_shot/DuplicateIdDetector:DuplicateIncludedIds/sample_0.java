package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.VIEW_INCLUDE;
import org.jetbrains.annotations.NonNull;

public class DuplicateIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if " +
            "layouts are combined with include tags, then the id's need to be unique " +
            "within any chain of included layouts, or `Activity#findViewById()` can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<File, LayoutInfo> fileToInfo = new HashMap<>();

    private static class LayoutInfo {
        final String name;
        final File file;
        final Map<String, Location> idToLocation = new HashMap<>();
        final List<IncludeInfo> includes = new ArrayList<>();

        LayoutInfo(String name, File file) {
            this.name = name;
            this.file = file;
        }
    }

    private static class IncludeInfo {
        final String targetLayout;
        final Location location;

        IncludeInfo(String targetLayout, Location location) {
            this.targetLayout = targetLayout;
            this.location = location;
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!com.android.SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        String idName = null;
        if (value.startsWith(NEW_ID_PREFIX)) {
            idName = value.substring(NEW_ID_PREFIX.length());
        } else if (value.startsWith(ID_PREFIX)) {
            idName = value.substring(ID_PREFIX.length());
        }

        if (idName != null && !idName.isEmpty()) {
            LayoutInfo info = getLayoutInfo(context);
            if (!info.idToLocation.containsKey(idName)) {
                info.idToLocation.put(idName, context.getLocation(attribute));
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(VIEW_INCLUDE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layoutAttr = element.getAttribute("layout");
        if (layoutAttr != null && layoutAttr.startsWith("@layout/")) {
            String includedName = layoutAttr.substring("@layout/".length());
            LayoutInfo info = getLayoutInfo(context);
            Location location = context.getLocation(element);
            info.includes.add(new IncludeInfo(includedName, location));
        }
    }

    private LayoutInfo getLayoutInfo(XmlContext context) {
        File file = context.file;
        LayoutInfo info = fileToInfo.get(file);
        if (info == null) {
            String name = file.getName();
            int dot = name.indexOf('.');
            String layoutName = dot >= 0 ? name.substring(0, dot) : name;
            info = new LayoutInfo(layoutName, file);
            fileToInfo.put(file, info);
        }
        return info;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Map<String, List<LayoutInfo>> layoutsByName = new HashMap<>();
        for (LayoutInfo info : fileToInfo.values()) {
            layoutsByName.computeIfAbsent(info.name, k -> new ArrayList<>()).add(info);
        }

        Set<String> reportedConflicts = new HashSet<>();

        for (LayoutInfo root : fileToInfo.values()) {
            Set<LayoutInfo> reachable = new HashSet<>();
            collectReachable(root, reachable, new HashSet<>(), layoutsByName);

            Map<String, List<LayoutInfo>> idToDefiningLayouts = new HashMap<>();
            for (LayoutInfo layout : reachable) {
                for (String id : layout.idToLocation.keySet()) {
                    idToDefiningLayouts.computeIfAbsent(id, k -> new ArrayList<>()).add(layout);
                }
            }

            for (Map.Entry<String, List<LayoutInfo>> entry : idToDefiningLayouts.entrySet()) {
                String id = entry.getKey();
                List<LayoutInfo> definitions = entry.getValue();
                if (definitions.size() > 1) {
                    for (int i = 0; i < definitions.size(); i++) {
                        for (int j = i + 1; j < definitions.size(); j++) {
                            LayoutInfo l1 = definitions.get(i);
                            LayoutInfo l2 = definitions.get(j);

                            if (l1.name.equals(l2.name)) {
                                continue;
                            }

                            String key = l1.file.compareTo(l2.file) < 0
                                ? l1.file.getPath() + ":" + l2.file.getPath() + ":" + id
                                : l2.file.getPath() + ":" + l1.file.getPath() + ":" + id;

                            if (reportedConflicts.add(key)) {
                                Location loc1 = l1.idToLocation.get(id);
                                Location loc2 = l2.idToLocation.get(id);

                                if (loc1 != null && loc2 != null) {
                                    loc1.setSecondary(loc2);
                                    loc2.setMessage("Duplicate ID defined here");
                                }

                                String message = String.format(
                                    "The id \"%1$s\" is defined in both %2$s and %3$s which are joined via include tags",
                                    id, l1.name, l2.name
                                );

                                context.report(ISSUE, loc1, message);
                            }
                        }
                    }
                }
            }
        }
    }

    private void collectReachable(LayoutInfo current, Set<LayoutInfo> reachable, Set<String> visitedNames, Map<String, List<LayoutInfo>> layoutsByName) {
        if (!visitedNames.add(current.name)) {
            return;
        }
        reachable.add(current);
        for (IncludeInfo include : current.includes) {
            List<LayoutInfo> targets = layoutsByName.get(include.targetLayout);
            if (targets != null) {
                for (LayoutInfo target : targets) {
                    collectReachable(target, reachable, visitedNames, layoutsByName);
                }
            }
        }
        visitedNames.remove(current.name);
    }
}