package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.SdkConstants;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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

    private final Map<String, LayoutInfo> layoutMap = new HashMap<>();
    private final Set<String> reportedPairs = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutMap.clear();
        reportedPairs.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        if (fileName.endsWith(SdkConstants.DOT_XML)) {
            String layoutName = fileName.substring(0, fileName.length() - SdkConstants.DOT_XML.length());
            LayoutInfo info = new LayoutInfo(layoutName, context.file);
            layoutMap.put(layoutName, info);
            collectIdsAndIncludes(context, document.getDocumentElement(), info);
        }
    }

    private void collectIdsAndIncludes(@NonNull XmlContext context, @Nullable Element element, @NonNull LayoutInfo info) {
        if (element == null) return;

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)) {
            Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
            if (idAttr != null) {
                String idValue = idAttr.getValue();
                String cleanId = stripIdPrefix(idValue);
                if (cleanId != null && !cleanId.isEmpty()) {
                    info.ids.add(cleanId);
                    info.idLocations.put(cleanId, context.getLocation(idAttr));
                }
            }
        }

        if (SdkConstants.VIEW_INCLUDE.equals(element.getTagName())) {
            Attr layoutAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT);
            if (layoutAttr == null) {
                layoutAttr = element.getAttributeNode(SdkConstants.ATTR_LAYOUT);
            }
            if (layoutAttr != null) {
                String layoutValue = layoutAttr.getValue();
                String cleanLayout = stripLayoutPrefix(layoutValue);
                if (cleanLayout != null && !cleanLayout.isEmpty()) {
                    info.includedLayouts.add(cleanLayout);
                    info.includeLocations.put(cleanLayout, context.getLocation(layoutAttr));
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIdsAndIncludes(context, (Element) child, info);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String name : layoutMap.keySet()) {
            inDegree.put(name, 0);
        }
        for (LayoutInfo info : layoutMap.values()) {
            for (String included : info.includedLayouts) {
                if (inDegree.containsKey(included)) {
                    inDegree.put(included, inDegree.get(included) + 1);
                }
            }
        }

        Set<String> checkedRoots = new HashSet<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                checkLayout(entry.getKey(), context);
                checkedRoots.add(entry.getKey());
            }
        }
        for (String name : layoutMap.keySet()) {
            if (!checkedRoots.contains(name)) {
                checkLayout(name, context);
            }
        }
    }

    private void checkLayout(@NonNull String layoutName, @NonNull Context context) {
        Set<String> visited = new HashSet<>();
        Map<String, String> idToLayout = new HashMap<>();
        Map<String, Location> idToLocation = new HashMap<>();
        dfs(layoutName, visited, idToLayout, idToLocation, context);
    }

    private void dfs(@NonNull String currentLayout, @NonNull Set<String> visited,
                     @NonNull Map<String, String> idToLayout, @NonNull Map<String, Location> idToLocation,
                     @NonNull Context context) {
        if (visited.contains(currentLayout)) {
            return;
        }
        visited.add(currentLayout);

        LayoutInfo info = layoutMap.get(currentLayout);
        if (info == null) return;

        for (String id : info.ids) {
            if (idToLayout.containsKey(id)) {
                String existingLayout = idToLayout.get(id);
                Location existingLoc = idToLocation.get(id);
                Location currentLoc = info.idLocations.get(id);

                String first = existingLayout.compareTo(currentLayout) < 0 ? existingLayout : currentLayout;
                String second = first.equals(existingLayout) ? currentLayout : existingLayout;
                String pairKey = id + ":" + first + ":" + second;

                if (!reportedPairs.contains(pairKey)) {
                    reportedPairs.add(pairKey);
                    String message = String.format(
                            "Duplicate id `%s` in layouts `%s` and `%s` which are joined via include",
                            id, existingLayout, currentLayout
                    );
                    if (currentLoc != null) {
                        if (existingLoc != null) {
                            currentLoc.setSecondary(existingLoc);
                        }
                        context.report(ISSUE, currentLoc, message);
                    }
                }
            } else {
                idToLayout.put(id, currentLayout);
                idToLocation.put(id, info.idLocations.get(id));
            }
        }

        for (String included : info.includedLayouts) {
            dfs(included, visited, idToLayout, idToLocation, context);
        }
    }

    @Nullable
    private static String stripIdPrefix(@Nullable String id) {
        if (id == null) return null;
        if (id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            return id.substring(SdkConstants.NEW_ID_PREFIX.length());
        } else if (id.startsWith(SdkConstants.ID_PREFIX)) {
            return id.substring(SdkConstants.ID_PREFIX.length());
        }
        return id;
    }

    @Nullable
    private static String stripLayoutPrefix(@Nullable String layout) {
        if (layout == null) return null;
        if (layout.startsWith(SdkConstants.LAYOUT_RESOURCE_PREFIX)) {
            return layout.substring(SdkConstants.LAYOUT_RESOURCE_PREFIX.length());
        }
        return layout;
    }

    private static class LayoutInfo {
        final String layoutName;
        final File file;
        final Set<String> ids = new LinkedHashSet<>();
        final Map<String, Location> idLocations = new HashMap<>();
        final Set<String> includedLayouts = new LinkedHashSet<>();
        final Map<String, Location> includeLocations = new HashMap<>();

        LayoutInfo(String layoutName, File file) {
            this.layoutName = layoutName;
            this.file = file;
        }
    }
}