package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
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

public class DuplicateIdDetector extends Detector implements XmlScanner {

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

    private final Map<String, List<LayoutInfo>> layoutMap = new HashMap<>();

    private static class LayoutInfo {
        final String name;
        final File file;
        final Set<String> ids = new HashSet<>();
        final Map<String, Location> idLocations = new HashMap<>();
        final Set<String> includes = new HashSet<>();

        LayoutInfo(String name, File file) {
            this.name = name;
            this.file = file;
        }
    }

    private LayoutInfo getLayoutInfo(XmlContext context) {
        File file = context.file;
        String fileName = file.getName();
        String layoutName = fileName;
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            layoutName = fileName.substring(0, dot);
        }

        List<LayoutInfo> list = layoutMap.get(layoutName);
        if (list == null) {
            list = new ArrayList<>();
            layoutMap.put(layoutName, list);
        }

        for (LayoutInfo info : list) {
            if (info.file.equals(file)) {
                return info;
            }
        }

        LayoutInfo info = new LayoutInfo(layoutName, file);
        list.add(info);
        return info;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }
        if (SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            String idValue = attribute.getValue();
            String id = stripIdPrefix(idValue);
            if (id != null && !id.isEmpty()) {
                LayoutInfo info = getLayoutInfo(context);
                info.ids.add(id);
                if (!info.idLocations.containsKey(id)) {
                    info.idLocations.put(id, context.getLocation(attribute));
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("include");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }
        if ("include".equals(element.getTagName())) {
            String layoutAttr = element.getAttribute("layout");
            if (layoutAttr != null && layoutAttr.startsWith("@layout/")) {
                String includedLayout = layoutAttr.substring("@layout/".length());
                LayoutInfo info = getLayoutInfo(context);
                info.includes.add(includedLayout);
            }
        }
    }

    private static String stripIdPrefix(String id) {
        if (id == null) return null;
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return null;
    }

    @Override
    public void afterCheckEachProject(Context context) {
        if (layoutMap.isEmpty()) {
            return;
        }

        Set<String> includedLayoutNames = new HashSet<>();
        for (List<LayoutInfo> list : layoutMap.values()) {
            for (LayoutInfo info : list) {
                includedLayoutNames.addAll(info.includes);
            }
        }

        Set<LayoutInfo> visitedAll = new HashSet<>();
        Set<String> reportedConflicts = new HashSet<>();

        for (Map.Entry<String, List<LayoutInfo>> entry : layoutMap.entrySet()) {
            if (!includedLayoutNames.contains(entry.getKey())) {
                for (LayoutInfo root : entry.getValue()) {
                    Set<LayoutInfo> visited = new HashSet<>();
                    Map<String, LayoutInfo> idToLayout = new HashMap<>();
                    dfs(context, root, visited, idToLayout, visitedAll, reportedConflicts);
                }
            }
        }

        for (List<LayoutInfo> list : layoutMap.values()) {
            for (LayoutInfo layout : list) {
                if (!visitedAll.contains(layout)) {
                    Set<LayoutInfo> visited = new HashSet<>();
                    Map<String, LayoutInfo> idToLayout = new HashMap<>();
                    dfs(context, layout, visited, idToLayout, visitedAll, reportedConflicts);
                }
            }
        }

        layoutMap.clear();
    }

    private void dfs(Context context, LayoutInfo current, Set<LayoutInfo> visited,
                     Map<String, LayoutInfo> idToLayout, Set<LayoutInfo> visitedAll,
                     Set<String> reportedConflicts) {
        if (!visited.add(current)) {
            return;
        }
        visitedAll.add(current);

        for (String id : current.ids) {
            if (idToLayout.containsKey(id)) {
                LayoutInfo otherLayout = idToLayout.get(id);
                if (current.name.equals(otherLayout.name)) {
                    continue;
                }
                String conflictKey = current.file.getPath() + "::" + otherLayout.file.getPath() + "::" + id;
                String conflictKeyReverse = otherLayout.file.getPath() + "::" + current.file.getPath() + "::" + id;
                if (!reportedConflicts.contains(conflictKey) && !reportedConflicts.contains(conflictKeyReverse)) {
                    reportedConflicts.add(conflictKey);

                    Location location = current.idLocations.get(id);
                    Location otherLocation = otherLayout.idLocations.get(id);
                    if (location != null) {
                        if (otherLocation != null) {
                            location.setSecondary(otherLocation, "Also defined here");
                        }
                        String message = String.format(
                                "Duplicate id `%s` in inclusion chain: both `%s` and `%s` define this id",
                                id, current.name, otherLayout.name
                        );
                        context.report(ISSUE, location, message);
                    }
                }
            } else {
                idToLayout.put(id, current);
            }
        }

        for (String include : current.includes) {
            List<LayoutInfo> includedLayouts = layoutMap.get(include);
            if (includedLayouts != null) {
                for (LayoutInfo included : includedLayouts) {
                    dfs(context, included, visited, idToLayout, visitedAll, reportedConflicts);
                }
            }
        }
    }
}