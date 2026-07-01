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

    private static final Map<String, LayoutInfo> sLayouts = new HashMap<>();

    private static class IdInfo {
        final String id;
        final String layoutName;
        final Location location;

        IdInfo(String id, String layoutName, Location location) {
            this.id = id;
            this.layoutName = layoutName;
            this.location = location;
        }
    }

    private static class IncludeInfo {
        final String layout;
        final Location location;

        IncludeInfo(String layout, Location location) {
            this.layout = layout;
            this.location = location;
        }
    }

    private static class LayoutInfo {
        final String name;
        final List<IdInfo> ids = new ArrayList<>();
        final List<IncludeInfo> includes = new ArrayList<>();

        LayoutInfo(String name) {
            this.name = name;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList("id", "layout");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
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
        sLayouts.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Set<String> reportedDuplicates = new HashSet<>();
        for (LayoutInfo root : sLayouts.values()) {
            Set<String> visitedLayouts = new HashSet<>();
            visitedLayouts.add(root.name);
            Map<String, IdInfo> idToSource = new HashMap<>();
            checkLayout(context, root, root, visitedLayouts, idToSource, reportedDuplicates);
        }
        sLayouts.clear();
    }

    private void checkLayout(
            Context context,
            LayoutInfo root,
            LayoutInfo current,
            Set<String> visitedLayouts,
            Map<String, IdInfo> idToSource,
            Set<String> reportedDuplicates) {

        for (IdInfo idInfo : current.ids) {
            if (idToSource.containsKey(idInfo.id)) {
                IdInfo original = idToSource.get(idInfo.id);
                String firstLayout = original.layoutName.compareTo(current.name) < 0 ? original.layoutName : current.name;
                String secondLayout = firstLayout.equals(original.layoutName) ? current.name : original.layoutName;
                String duplicateKey = idInfo.id + ":" + firstLayout + ":" + secondLayout;

                if (!reportedDuplicates.contains(duplicateKey)) {
                    reportedDuplicates.add(duplicateKey);

                    String message = String.format(
                            "Duplicate id `%s` in include chain: also defined in `%s`",
                            idInfo.id, original.layoutName
                    );

                    Location location = idInfo.location;
                    location.setSecondary(original.location);
                    context.report(ISSUE, location, message);
                }
            } else {
                idToSource.put(idInfo.id, idInfo);
            }
        }

        for (IncludeInfo include : current.includes) {
            String includedName = include.layout;
            if (visitedLayouts.contains(includedName)) {
                continue;
            }
            LayoutInfo includedLayout = sLayouts.get(includedName);
            if (includedLayout != null) {
                visitedLayouts.add(includedName);
                checkLayout(context, root, includedLayout, visitedLayouts, idToSource, reportedDuplicates);
                visitedLayouts.remove(includedName);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - 4);
        LayoutInfo layoutInfo = sLayouts.get(layoutName);
        if (layoutInfo == null) {
            layoutInfo = new LayoutInfo(layoutName);
            sLayouts.put(layoutName, layoutInfo);
        }

        String name = attribute.getLocalName();
        String value = attribute.getValue();
        if ("id".equals(name)) {
            String id = value;
            if (id.startsWith("@+id/")) {
                id = id.substring(5);
            } else if (id.startsWith("@id/")) {
                id = id.substring(4);
            }
            Location location = context.getLocation(attribute);
            layoutInfo.ids.add(new IdInfo(id, layoutName, location));
        } else if ("layout".equals(name)) {
            Element element = attribute.getOwnerElement();
            if (element != null && "include".equals(element.getTagName())) {
                String includedLayout = value;
                if (includedLayout.startsWith("@layout/")) {
                    includedLayout = includedLayout.substring(8);
                    Location location = context.getLocation(attribute);
                    layoutInfo.includes.add(new IncludeInfo(includedLayout, location));
                }
            }
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(Object o) {
        return 0;
    }
}