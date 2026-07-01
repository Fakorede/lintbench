package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if " +
                    "layouts are combined with include tags, then the id's need to be unique " +
                    "within any chain of included layouts, or Activity#findViewById() can " +
                    "return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final java.util.Map<String, java.util.List<LayoutInfo>> layoutMap = new java.util.HashMap<>();
    private final java.util.Set<String> reportedDuplications = new java.util.HashSet<>();
    private LayoutInfo currentLayout;

    private static class LayoutInfo {
        final java.io.File file;
        final java.util.Set<String> ids = new java.util.HashSet<>();
        final java.util.Set<String> includes = new java.util.HashSet<>();
        final java.util.Map<String, Location> idToLocation = new java.util.HashMap<>();
        final java.util.Map<String, Location> includeToLocation = new java.util.HashMap<>();

        LayoutInfo(java.io.File file) {
            this.file = file;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
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
    public void beforeCheckFile(@NonNull Context context) {
        currentLayout = new LayoutInfo(context.file);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (currentLayout != null) {
            String layoutName = getLayoutName(context.file);
            java.util.List<LayoutInfo> infos = layoutMap.get(layoutName);
            if (infos == null) {
                infos = new java.util.ArrayList<>();
                layoutMap.put(layoutName, infos);
            }
            infos.add(currentLayout);
            currentLayout = null;
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutMap.clear();
        reportedDuplications.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (java.util.List<LayoutInfo> infos : layoutMap.values()) {
            for (LayoutInfo info : infos) {
                checkLayout(info, context);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"include".equals(element.getTagName())) {
            return;
        }
        String layout = element.getAttribute("layout");
        if (layout.isEmpty()) {
            layout = element.getAttributeNS("http://schemas.android.com/apk/res/android", "layout");
        }
        if (layout.startsWith("@layout/")) {
            String includedLayout = layout.substring("@layout/".length());
            if (currentLayout != null) {
                currentLayout.includes.add(includedLayout);
                currentLayout.includeToLocation.put(includedLayout, context.getLocation(element));
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"id".equals(attribute.getLocalName())) {
            return;
        }
        if (!"http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            return;
        }
        String idValue = attribute.getValue();
        String id = null;
        if (idValue.startsWith("@+id/")) {
            id = idValue.substring("@+id/".length());
        } else if (idValue.startsWith("@id/")) {
            id = idValue.substring("@id/".length());
        }
        if (id != null && currentLayout != null) {
            currentLayout.ids.add(id);
            currentLayout.idToLocation.put(id, context.getLocation(attribute));
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(Object other) {
        return 0;
    }

    private String getLayoutName(java.io.File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    private void checkLayout(LayoutInfo root, Context context) {
        java.util.Map<String, LayoutInfo> idToLayout = new java.util.HashMap<>();
        java.util.Set<LayoutInfo> visited = new java.util.HashSet<>();
        dfs(root, idToLayout, visited, context, root);
    }

    private void dfs(LayoutInfo current, java.util.Map<String, LayoutInfo> idToLayout, java.util.Set<LayoutInfo> visited, Context context, LayoutInfo root) {
        if (!visited.add(current)) {
            return;
        }

        for (String id : current.ids) {
            if (idToLayout.containsKey(id)) {
                LayoutInfo other = idToLayout.get(id);
                if (other != current) {
                    reportDuplicate(context, id, current, other);
                }
            } else {
                idToLayout.put(id, current);
            }
        }

        for (String include : current.includes) {
            java.util.List<LayoutInfo> includedInfos = layoutMap.get(include);
            if (includedInfos != null) {
                for (LayoutInfo includedInfo : includedInfos) {
                    dfs(includedInfo, idToLayout, visited, context, root);
                }
            }
        }
    }

    private void reportDuplicate(Context context, String id, LayoutInfo layout1, LayoutInfo layout2) {
        java.io.File file1 = layout1.file;
        java.io.File file2 = layout2.file;

        String path1 = file1.getAbsoluteFile().getAbsolutePath();
        String path2 = file2.getAbsoluteFile().getAbsolutePath();

        String key;
        if (path1.compareTo(path2) < 0) {
            key = id + "|" + path1 + "|" + path2;
        } else {
            key = id + "|" + path2 + "|" + path1;
        }

        if (!reportedDuplications.add(key)) {
            return;
        }

        Location location1 = layout1.idToLocation.get(id);
        Location location2 = layout2.idToLocation.get(id);

        if (location1 != null && location2 != null) {
            location1.setSecondary(location2);
            location2.setMessage("Duplicate id defined here");
        }

        Location reportLocation = location1 != null ? location1 : location2;
        if (reportLocation != null) {
            String message = String.format(
                    "Duplicate id `%s` in inclusion chain (defined in `%s` and `%s`)",
                    "@+id/" + id,
                    file1.getName(),
                    file2.getName());
            context.report(ISSUE, reportLocation, message);
        }
    }
}