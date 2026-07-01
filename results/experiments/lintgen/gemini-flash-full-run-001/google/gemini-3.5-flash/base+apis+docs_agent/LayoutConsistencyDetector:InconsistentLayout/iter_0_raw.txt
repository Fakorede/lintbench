package com.android.tools.lint.checks;

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
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
        "There **are** cases where this is intentional. For example, you " +
        "may have a dedicated large tablet layout which adds some extra " +
        "widgets that are not present in the phone version of the layout. " +
        "As long as the code accessing the layout resource is careful to " +
        "handle this properly, it is valid. In that case, you can suppress " +
        "this lint check for the given extra or missing views, or the whole " +
        "layout",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            LayoutConsistencyDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    private final Map<String, Map<String, LayoutInfo>> layoutMap = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - 4);
        String folderName = context.file.getParentFile().getName();

        LayoutInfo info = new LayoutInfo();
        info.file = context.file;
        info.rootLocation = context.getLocation(root);

        findIds(context, root, info.idLocations);

        synchronized (layoutMap) {
            Map<String, LayoutInfo> variations = layoutMap.computeIfAbsent(layoutName, k -> new HashMap<>());
            variations.put(folderName, info);
        }
    }

    private void findIds(XmlContext context, Element element, Map<String, Location> idLocations) {
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id != null && !id.isEmpty()) {
            String cleanId = stripIdPrefix(id);
            if (!cleanId.isEmpty()) {
                Attr idAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "id");
                Location location = idAttr != null ? context.getLocation(idAttr) : context.getLocation(element);
                idLocations.put(cleanId, location);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                findIds(context, (Element) child, idLocations);
            }
        }
    }

    private String stripIdPrefix(String id) {
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        synchronized (layoutMap) {
            for (Map.Entry<String, Map<String, LayoutInfo>> entry : layoutMap.entrySet()) {
                String layoutName = entry.getKey();
                Map<String, LayoutInfo> variations = entry.getValue();
                if (variations.size() <= 1) {
                    continue;
                }

                // Find all unique IDs across all variations
                Set<String> allIds = new HashSet<>();
                for (LayoutInfo info : variations.values()) {
                    allIds.addAll(info.idLocations.keySet());
                }

                for (String id : allIds) {
                    List<String> missingFolders = new ArrayList<>();
                    List<String> hasFolders = new ArrayList<>();
                    for (Map.Entry<String, LayoutInfo> varEntry : variations.entrySet()) {
                        String folderName = varEntry.getKey();
                        LayoutInfo info = varEntry.getValue();
                        if (info.idLocations.containsKey(id)) {
                            hasFolders.add(folderName);
                        } else {
                            missingFolders.add(folderName);
                        }
                    }

                    if (!missingFolders.isEmpty()) {
                        Collections.sort(missingFolders);
                        String missingList = String.join(", ", missingFolders);

                        for (String hasFolder : hasFolders) {
                            LayoutInfo info = variations.get(hasFolder);
                            Location location = info.idLocations.get(id);
                            if (location != null) {
                                String message = String.format(
                                    "ID `%s` is missing in %s",
                                    id, missingList
                                );
                                context.report(ISSUE, location, message);
                            }
                        }
                    }
                }
            }
            layoutMap.clear();
        }
    }

    private static class LayoutInfo {
        File file;
        Location rootLocation;
        final Map<String, Location> idLocations = new HashMap<>();
    }
}