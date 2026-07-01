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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NonNull;

public class LayoutConsistencyDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple " +
            "resource folders, specifies the same set of widgets.\n\n" +
            "This finds cases where you have accidentally forgotten to add " +
            "a widget to all variations of the layout, which could result " +
            "in a runtime crash for some resource configurations when a " +
            "`findViewById()` fails.\n\n" +
            "There *are* cases where this is intentional. For example, you " +
            "may have a dedicated large tablet layout which adds some extra " +
            "widgets that are not present in the phone version of the layout. " +
            "As long as the code accessing the layout resource is careful to " +
            "handle this properly, it is valid. In that case, you can suppress " +
            "this lint check for the given extra or missing views, or the whole " +
            "layout.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, Map<String, Map<String, Location>>> layoutToFolderToIdMap = new HashMap<>();
    private final Map<String, Map<String, Location>> layoutToFileLocation = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String layoutName = dot != -1 ? fileName.substring(0, dot) : fileName;
        String folderName = context.file.getParentFile().getName();

        Map<String, Map<String, Location>> folderToIdMap = layoutToFolderToIdMap.computeIfAbsent(layoutName, k -> new HashMap<>());
        Map<String, Location> idToLocation = folderToIdMap.computeIfAbsent(folderName, k -> new HashMap<>());

        Element root = document.getDocumentElement();
        if (root != null) {
            layoutToFileLocation.computeIfAbsent(layoutName, k -> new HashMap<>()).put(folderName, context.getLocation(root));
            collectIds(context, root, idToLocation);
        }
    }

    private void collectIds(XmlContext context, Element element, Map<String, Location> idToLocation) {
        if (element == null) return;
        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            String cleanId = stripIdPrefix(id);
            if (!cleanId.isEmpty() && !idToLocation.containsKey(cleanId)) {
                idToLocation.put(cleanId, context.getLocation(idAttr));
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds(context, (Element) child, idToLocation);
            }
        }
    }

    private static String stripIdPrefix(String id) {
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
        for (Map.Entry<String, Map<String, Map<String, Location>>> entry : layoutToFolderToIdMap.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Map<String, Location>> folderToIdMap = entry.getValue();
            if (folderToIdMap.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Map<String, Location> idMap : folderToIdMap.values()) {
                allIds.addAll(idMap.keySet());
            }

            for (Map.Entry<String, Map<String, Location>> folderEntry : folderToIdMap.entrySet()) {
                String folderName = folderEntry.getKey();
                Map<String, Location> idMap = folderEntry.getValue();

                for (String expectedId : allIds) {
                    if (!idMap.containsKey(expectedId)) {
                        Location fileLocation = null;
                        Map<String, Location> fileLocations = layoutToFileLocation.get(layoutName);
                        if (fileLocations != null) {
                            fileLocation = fileLocations.get(folderName);
                        }

                        if (fileLocation == null) {
                            continue;
                        }

                        List<String> presentIn = new ArrayList<>();
                        for (Map.Entry<String, Map<String, Location>> otherFolderEntry : folderToIdMap.entrySet()) {
                            if (otherFolderEntry.getValue().containsKey(expectedId)) {
                                presentIn.add(otherFolderEntry.getKey());
                            }
                        }

                        String message = String.format(
                                "Layout `%s` in `%s` is missing ID `%s` (defined in %s)",
                                layoutName, folderName, expectedId, formatFolderList(presentIn)
                        );

                        context.report(ISSUE, fileLocation, message);
                    }
                }
            }
        }
    }

    private static String formatFolderList(List<String> folders) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < folders.size(); i++) {
            if (i > 0) {
                if (i == folders.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append("`").append(folders.get(i)).append("`");
        }
        return sb.toString();
    }
}