package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateIdDetector extends LayoutDetector {

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
    private final Set<String> reportedDuplicates = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutMap.clear();
        reportedDuplicates.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String layoutName = dot >= 0 ? fileName.substring(0, dot) : fileName;

        LayoutInfo info = layoutMap.get(layoutName);
        if (info == null) {
            info = new LayoutInfo(layoutName, context.file);
            layoutMap.put(layoutName, info);
        }

        collectIdsAndIncludes(context, document.getDocumentElement(), info);
    }

    private void collectIdsAndIncludes(XmlContext context, Element element, LayoutInfo info) {
        if (element == null) return;

        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            String id = stripId(idValue);
            if (!id.isEmpty() && !info.ids.containsKey(id)) {
                info.ids.put(id, context.getLocation(idAttr));
            }
        }

        if (SdkConstants.VIEW_INCLUDE.equals(element.getTagName())) {
            Attr layoutAttr = element.getAttributeNodeNS(null, SdkConstants.ATTR_LAYOUT);
            if (layoutAttr == null) {
                layoutAttr = element.getAttributeNode(SdkConstants.ATTR_LAYOUT);
            }
            if (layoutAttr != null) {
                String layoutVal = layoutAttr.getValue();
                if (layoutVal.startsWith(SdkConstants.LAYOUT_RESOURCE_PREFIX)) {
                    String includedLayout = layoutVal.substring(SdkConstants.LAYOUT_RESOURCE_PREFIX.length());
                    info.includes.put(includedLayout, context.getLocation(layoutAttr));
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

    private static String stripId(String id) {
        if (id == null) return "";
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (LayoutInfo root : layoutMap.values()) {
            Map<String, String> idToSource = new HashMap<>();
            Set<String> visited = new HashSet<>();
            collectTransitiveIds(root.name, idToSource, visited, context, root);
        }
    }

    private void collectTransitiveIds(String currentLayout, Map<String, String> idToSource, Set<String> visited, Context context, LayoutInfo rootLayout) {
        if (visited.contains(currentLayout)) {
            return;
        }
        visited.add(currentLayout);

        LayoutInfo info = layoutMap.get(currentLayout);
        if (info == null) return;

        for (String id : info.ids.keySet()) {
            if (idToSource.containsKey(id)) {
                String originalLayout = idToSource.get(id);
                reportDuplicate(context, rootLayout, currentLayout, id, originalLayout);
            } else {
                idToSource.put(id, currentLayout);
            }
        }

        for (String included : info.includes.keySet()) {
            collectTransitiveIds(included, idToSource, visited, context, rootLayout);
        }
    }

    private void reportDuplicate(Context context, LayoutInfo rootLayout, String currentLayout, String id, String originalLayout) {
        String signature = id + "@" + (currentLayout.compareTo(originalLayout) < 0
                ? currentLayout + "-" + originalLayout
                : originalLayout + "-" + currentLayout);
        if (reportedDuplicates.contains(signature)) {
            return;
        }
        reportedDuplicates.add(signature);

        LayoutInfo currentInfo = layoutMap.get(currentLayout);
        LayoutInfo originalInfo = layoutMap.get(originalLayout);
        if (currentInfo == null || originalInfo == null) return;

        Location currentLocation = currentInfo.ids.get(id);
        Location originalLocation = originalInfo.ids.get(id);

        if (currentLocation != null) {
            String message;
            if (currentLayout.equals(originalLayout)) {
                message = String.format("Duplicate id `%s` in layout `%s`", id, currentLayout);
            } else {
                message = String.format(
                        "Duplicate id `%s` inside include hierarchy: defined in `%s` and `%s` (combined via layout `%s`)",
                        id, currentLayout, originalLayout, rootLayout.name);
            }

            if (originalLocation != null) {
                currentLocation.setSecondary(originalLocation);
                originalLocation.setMessage("Original definition here");
            }

            context.report(ISSUE, currentLocation, message);
        }
    }

    private static class LayoutInfo {
        final String name;
        final File file;
        final Map<String, Location> ids = new HashMap<>();
        final Map<String, Location> includes = new HashMap<>();

        LayoutInfo(String name, File file) {
            this.name = name;
            this.file = file;
        }
    }
}