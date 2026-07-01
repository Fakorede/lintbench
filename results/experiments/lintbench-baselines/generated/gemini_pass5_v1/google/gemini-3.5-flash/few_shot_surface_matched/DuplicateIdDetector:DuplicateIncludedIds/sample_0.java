package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the id's need to be unique "
                            + "within any chain of included layouts, or `Activity#findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            DuplicateIdDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    private final java.util.Map<String, java.util.Set<String>> mLayoutToIds = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.Map<String, Location>> mLayoutToIdLocations = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.List<String>> mLayoutToIncludes = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.List<Location>> mLayoutToIncludeLocations = new java.util.HashMap<>();

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList(com.android.SdkConstants.ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(com.android.SdkConstants.VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        // Required override
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        // Required override
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayoutToIds.clear();
        mLayoutToIdLocations.clear();
        mLayoutToIncludes.clear();
        mLayoutToIncludeLocations.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String rootLayout : mLayoutToIds.keySet()) {
            java.util.Set<String> visited = new java.util.HashSet<>();
            java.util.Map<String, String> idToDefiningLayout = new java.util.HashMap<>();
            dfs(rootLayout, visited, idToDefiningLayout, context);
        }
    }

    private void dfs(String currentLayout, java.util.Set<String> visited, java.util.Map<String, String> idToDefiningLayout, Context context) {
        if (visited.contains(currentLayout)) {
            return;
        }
        visited.add(currentLayout);

        java.util.Set<String> ids = mLayoutToIds.get(currentLayout);
        if (ids != null) {
            for (String id : ids) {
                if (idToDefiningLayout.containsKey(id)) {
                    String otherLayout = idToDefiningLayout.get(id);
                    Location location = null;
                    java.util.Map<String, Location> locs = mLayoutToIdLocations.get(currentLayout);
                    if (locs != null) {
                        location = locs.get(id);
                    }
                    if (location == null) {
                        continue;
                    }

                    Location secondary = null;
                    java.util.Map<String, Location> otherLocs = mLayoutToIdLocations.get(otherLayout);
                    if (otherLocs != null) {
                        secondary = otherLocs.get(id);
                    }
                    if (secondary != null) {
                        location.setSecondary(secondary);
                    }

                    String message = String.format(
                            "Duplicate id `%s` across included layouts: `%s` defines it, and it is also defined in included/including layout `%s`",
                            id, currentLayout, otherLayout);
                    context.report(ISSUE, location, message);
                } else {
                    idToDefiningLayout.put(id, currentLayout);
                }
            }
        }

        java.util.List<String> includes = mLayoutToIncludes.get(currentLayout);
        if (includes != null) {
            for (String included : includes) {
                java.util.Map<String, String> childMap = new java.util.HashMap<>(idToDefiningLayout);
                java.util.Set<String> childVisited = new java.util.HashSet<>(visited);
                dfs(included, childVisited, childMap, context);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (com.android.SdkConstants.VIEW_INCLUDE.equals(element.getTagName())) {
            String layoutAttr = element.getAttribute(com.android.SdkConstants.ATTR_LAYOUT);
            if (layoutAttr.isEmpty()) {
                layoutAttr = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_LAYOUT);
            }
            if (!layoutAttr.isEmpty() && layoutAttr.startsWith(com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layoutAttr.substring(com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX.length());
                String currentLayout = getLayoutName(context);
                mLayoutToIncludes.computeIfAbsent(currentLayout, k -> new java.util.ArrayList<>()).add(includedLayout);
                mLayoutToIncludeLocations.computeIfAbsent(currentLayout, k -> new java.util.ArrayList<>()).add(context.getLocation(element));
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (com.android.SdkConstants.ATTR_ID.equals(name)) {
            String id = attribute.getValue();
            if (id.startsWith(com.android.SdkConstants.NEW_ID_PREFIX)) {
                id = id.substring(com.android.SdkConstants.NEW_ID_PREFIX.length());
            } else if (id.startsWith(com.android.SdkConstants.ID_PREFIX)) {
                id = id.substring(com.android.SdkConstants.ID_PREFIX.length());
            }
            String layout = getLayoutName(context);
            mLayoutToIds.computeIfAbsent(layout, k -> new java.util.HashSet<>()).add(id);

            java.util.Map<String, Location> idLocs = mLayoutToIdLocations.computeIfAbsent(layout, k -> new java.util.HashMap<>());
            if (!idLocs.containsKey(id)) {
                idLocs.put(id, context.getLocation(attribute));
            }
        }
    }

    private String getLayoutName(XmlContext context) {
        String name = context.file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(@NonNull Detector other) {
        return this.getClass().getName().compareTo(other.getClass().getName());
    }
}