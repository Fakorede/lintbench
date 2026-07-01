package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final Map<String, List<LayoutInfo>> layoutsByName = new HashMap<>();

    private static class LayoutInfo {
        String name;
        File file;
        Map<String, Location> ids = new HashMap<>();
        List<IncludeInfo> includes = new ArrayList<>();
    }

    private static class IncludeInfo {
        String layoutName;
        Location location;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        layoutsByName.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }
        String layoutName = getLayoutName(context.file);
        LayoutInfo info = new LayoutInfo();
        info.name = layoutName;
        info.file = context.file;

        Element root = document.getDocumentElement();
        if (root != null) {
            collectLayoutInfo(context, root, info);
        }

        layoutsByName.computeIfAbsent(layoutName, k -> new ArrayList<>()).add(info);
    }

    private void collectLayoutInfo(XmlContext context, Element element, LayoutInfo info) {
        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            String cleanId = stripIdPrefix(id);
            if (!cleanId.isEmpty()) {
                if (!info.ids.containsKey(cleanId)) {
                    info.ids.put(cleanId, context.getLocation(idAttr));
                }
            }
        }

        if (SdkConstants.VIEW_INCLUDE.equals(element.getTagName())) {
            Attr layoutAttr = element.getAttributeNode(SdkConstants.ATTR_LAYOUT);
            if (layoutAttr != null) {
                String layoutVal = layoutAttr.getValue();
                String includedLayout = stripLayoutPrefix(layoutVal);
                if (!includedLayout.isEmpty()) {
                    IncludeInfo inc = new IncludeInfo();
                    inc.layoutName = includedLayout;
                    inc.location = context.getLocation(layoutAttr);
                    info.includes.add(inc);
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectLayoutInfo(context, (Element) child, info);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Set<String> reportedConflicts = new HashSet<>();

        for (List<LayoutInfo> infos : layoutsByName.values()) {
            for (LayoutInfo root : infos) {
                Set<LayoutInfo> reachable = new LinkedHashSet<>();
                collectReachable(root, reachable, new HashSet<>());

                Map<String, LayoutInfo> idToLayout = new HashMap<>();
                for (LayoutInfo layout : reachable) {
                    for (String id : layout.ids.keySet()) {
                        if (idToLayout.containsKey(id)) {
                            LayoutInfo otherLayout = idToLayout.get(id);
                            if (otherLayout != layout) {
                                String conflictKey = getConflictKey(layout.name, otherLayout.name, id);
                                if (!reportedConflicts.contains(conflictKey)) {
                                    reportedConflicts.add(conflictKey);

                                    Location loc1 = layout.ids.get(id);
                                    Location loc2 = otherLayout.ids.get(id);

                                    String message = String.format(
                                        "Duplicate id `%s` in reachable layouts `%s` and `%s` (via include chain)",
                                        id, layout.name, otherLayout.name
                                    );

                                    if (loc1 != null && loc2 != null) {
                                        loc1.setSecondary(loc2);
                                        loc2.setMessage("Also defined here");
                                    }

                                    if (loc1 != null) {
                                        context.report(ISSUE, loc1, message);
                                    }
                                }
                            }
                        } else {
                            idToLayout.put(id, layout);
                        }
                    }
                }
            }
        }

        layoutsByName.clear();
    }

    private void collectReachable(LayoutInfo current, Set<LayoutInfo> reachable, Set<LayoutInfo> path) {
        if (path.contains(current)) {
            return;
        }
        reachable.add(current);
        path.add(current);
        for (IncludeInfo inc : current.includes) {
            List<LayoutInfo> includedLayouts = resolveIncludes(current, inc.layoutName);
            for (LayoutInfo included : includedLayouts) {
                collectReachable(included, reachable, path);
            }
        }
        path.remove(current);
    }

    private List<LayoutInfo> resolveIncludes(LayoutInfo current, String targetName) {
        List<LayoutInfo> candidates = layoutsByName.get(targetName);
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        String currentFolder = current.file.getParentFile().getName();

        List<LayoutInfo> matches = new ArrayList<>();
        for (LayoutInfo cand : candidates) {
            if (cand.file.getParentFile().getName().equals(currentFolder)) {
                matches.add(cand);
            }
        }
        if (!matches.isEmpty()) {
            return matches;
        }

        for (LayoutInfo cand : candidates) {
            if (cand.file.getParentFile().getName().equals("layout")) {
                matches.add(cand);
            }
        }
        if (!matches.isEmpty()) {
            return matches;
        }

        return candidates;
    }

    private String getLayoutName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    private String stripIdPrefix(String id) {
        if (id == null) return "";
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    private String stripLayoutPrefix(String layout) {
        if (layout == null) return "";
        if (layout.startsWith("@layout/")) {
            return layout.substring(8);
        }
        return layout;
    }

    private String getConflictKey(String layout1, String layout2, String id) {
        if (layout1.compareTo(layout2) < 0) {
            return layout1 + ":" + layout2 + ":" + id;
        } else {
            return layout2 + ":" + layout1 + ":" + id;
        }
    }
}