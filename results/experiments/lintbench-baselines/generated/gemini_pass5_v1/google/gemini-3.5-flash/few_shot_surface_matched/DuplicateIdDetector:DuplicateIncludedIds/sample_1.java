package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner, Comparable<DuplicateIdDetector> {

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
                            DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final java.util.Map<String, LayoutInfo> mLayouts = new java.util.HashMap<>();
    private final java.util.Set<String> mReported = new java.util.HashSet<>();
    private LayoutInfo mCurrentLayout;

    private static class LayoutInfo {
        final String name;
        final java.io.File file;
        final java.util.Map<String, Location> idToLocation = new java.util.HashMap<>();
        final java.util.List<String> includes = new java.util.ArrayList<>();

        LayoutInfo(String name, java.io.File file) {
            this.name = name;
            this.file = file;
        }
    }

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("id");
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("include");
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        String fileName = context.file.getName();
        if (fileName.endsWith(".xml")) {
            String layoutName = fileName.substring(0, fileName.length() - 4);
            mCurrentLayout = new LayoutInfo(layoutName, context.file);
            mLayouts.put(layoutName, mCurrentLayout);
        } else {
            mCurrentLayout = null;
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        mCurrentLayout = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayouts.clear();
        mReported.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        mReported.clear();
        for (LayoutInfo layout : mLayouts.values()) {
            java.util.Map<String, String> idToSourceLayout = new java.util.HashMap<>();
            java.util.Set<String> path = new java.util.HashSet<>();
            collectIds(layout, path, idToSourceLayout, context);
        }
        mLayouts.clear();
    }

    private void collectIds(
            @NonNull LayoutInfo layout,
            @NonNull java.util.Set<String> path,
            @NonNull java.util.Map<String, String> idToSourceLayout,
            @NonNull Context context) {
        if (path.contains(layout.name)) {
            return;
        }
        path.add(layout.name);

        for (java.util.Map.Entry<String, Location> entry : layout.idToLocation.entrySet()) {
            String id = entry.getKey();
            Location location = entry.getValue();
            if (idToSourceLayout.containsKey(id)) {
                String otherLayout = idToSourceLayout.get(id);
                String key = id + ":" + layout.name + ":" + otherLayout;
                String altKey = id + ":" + otherLayout + ":" + layout.name;
                if (!mReported.contains(key) && !mReported.contains(altKey)) {
                    mReported.add(key);
                    String message = String.format(
                            "Duplicate id `%s` within layout include hierarchy (defined in `%s` and `%s`)",
                            id, layout.name, otherLayout);
                    context.report(ISSUE, location, message);
                }
            } else {
                idToSourceLayout.put(id, layout.name);
            }
        }

        for (String include : layout.includes) {
            LayoutInfo includedLayout = mLayouts.get(include);
            if (includedLayout != null) {
                collectIds(includedLayout, path, idToSourceLayout, context);
            }
        }

        path.remove(layout.name);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        if (mCurrentLayout == null) {
            return;
        }
        String layout = element.getAttribute("layout");
        if (layout != null && !layout.isEmpty()) {
            if (layout.startsWith("@layout/")) {
                String includedLayout = layout.substring("@layout/".length());
                mCurrentLayout.includes.add(includedLayout);
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull org.w3c.dom.Attr attribute) {
        if (mCurrentLayout == null) {
            return;
        }
        String idValue = attribute.getValue();
        String idName = null;
        if (idValue.startsWith("@+id/")) {
            idName = idValue.substring("@+id/".length());
        } else if (idValue.startsWith("@id/")) {
            idName = idValue.substring("@id/".length());
        }
        if (idName != null && !idName.isEmpty()) {
            Location location = context.getLocation(attribute);
            mCurrentLayout.idToLocation.put(idName, location);
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(@NonNull DuplicateIdDetector other) {
        return this.toString().compareTo(other.toString());
    }
}