package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, LayoutInfo> layoutMap = new HashMap<>();
    private final Set<String> reported = new HashSet<>();

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String name = context.file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }

        LayoutInfo layoutInfo = new LayoutInfo();
        layoutInfo.name = name;
        layoutInfo.file = context.file;

        collectIdsAndIncludes(context, document.getDocumentElement(), layoutInfo);

        layoutMap.put(name, layoutInfo);
    }

    private void collectIdsAndIncludes(XmlContext context, Element element, LayoutInfo layoutInfo) {
        if (element == null) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)) {
            Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
            if (idAttr != null) {
                String idValue = idAttr.getValue();
                String id = stripIdPrefix(idValue);
                if (id != null && !id.isEmpty()) {
                    Location location = context.getLocation(idAttr);
                    layoutInfo.ids.put(id, location);
                }
            }
        }

        if (element.getTagName().equals(SdkConstants.VIEW_INCLUDE)) {
            String layoutVal = element.getAttribute(SdkConstants.ATTR_LAYOUT);
            if (layoutVal == null || layoutVal.isEmpty()) {
                layoutVal = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT);
            }
            if (layoutVal != null && !layoutVal.isEmpty()) {
                String targetLayout = stripLayoutPrefix(layoutVal);
                if (targetLayout != null && !targetLayout.isEmpty()) {
                    IncludeInfo includeInfo = new IncludeInfo();
                    includeInfo.targetLayout = targetLayout;
                    includeInfo.location = context.getLocation(element);
                    layoutInfo.includes.add(includeInfo);
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIdsAndIncludes(context, (Element) child, layoutInfo);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (LayoutInfo root : layoutMap.values()) {
            Set<String> visited = new HashSet<>();
            Map<String, String> idToLayout = new HashMap<>();
            Map<String, Location> idToLocation = new HashMap<>();
            dfs(root, root, visited, idToLayout, idToLocation, context);
        }
        layoutMap.clear();
        reported.clear();
    }

    private void dfs(LayoutInfo root, LayoutInfo current, Set<String> visited,
                     Map<String, String> idToLayout, Map<String, Location> idToLocation,
                     Context context) {
        if (visited.contains(current.name)) {
            return;
        }
        visited.add(current.name);

        for (Map.Entry<String, Location> entry : current.ids.entrySet()) {
            String id = entry.getKey();
            Location loc = entry.getValue();
            if (idToLayout.containsKey(id)) {
                String otherLayout = idToLayout.get(id);
                Location otherLoc = idToLocation.get(id);
                reportDuplicate(root, current.name, otherLayout, id, loc, otherLoc, context);
            } else {
                idToLayout.put(id, current.name);
                idToLocation.put(id, loc);
            }
        }

        for (IncludeInfo include : current.includes) {
            LayoutInfo includedLayout = layoutMap.get(include.targetLayout);
            if (includedLayout != null) {
                dfs(root, includedLayout, visited, idToLayout, idToLocation, context);
            }
        }
    }

    private void reportDuplicate(LayoutInfo root, String layout1, String layout2, String id,
                                 Location loc1, Location loc2, Context context) {
        if (layout1.equals(layout2)) {
            return;
        }

        String first = layout1.compareTo(layout2) <= 0 ? layout1 : layout2;
        String second = first.equals(layout1) ? layout2 : layout1;
        Location firstLoc = first.equals(layout1) ? loc1 : loc2;
        Location secondLoc = first.equals(layout1) ? loc2 : loc1;

        String key = first + "::" + second + "::" + id;
        if (reported.contains(key)) {
            return;
        }
        reported.add(key);

        String message = String.format(
                "Duplicate id `%s` in included layouts `%s` and `%s` (combined in `%s`)",
                id, first, second, root.name
        );

        Location location = secondLoc;
        if (firstLoc != null) {
            firstLoc.setMessage("Also defined here");
            secondLoc.setSecondary(firstLoc);
        }

        context.report(ISSUE, location, message);
    }

    private String stripIdPrefix(String id) {
        if (id == null) return null;
        int index = id.indexOf('/');
        if (index >= 0) {
            return id.substring(index + 1);
        }
        return id;
    }

    private String stripLayoutPrefix(String layout) {
        if (layout == null) return null;
        int index = layout.indexOf('/');
        if (index >= 0) {
            return layout.substring(index + 1);
        }
        return layout;
    }

    private static class LayoutInfo {
        String name;
        File file;
        Map<String, Location> ids = new HashMap<>();
        List<IncludeInfo> includes = new ArrayList<>();
    }

    private static class IncludeInfo {
        String targetLayout;
        Location location;
    }
}