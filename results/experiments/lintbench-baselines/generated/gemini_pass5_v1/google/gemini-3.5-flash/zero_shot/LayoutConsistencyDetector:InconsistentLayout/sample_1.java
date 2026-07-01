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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

public class LayoutConsistencyDetector extends Detector implements Detector.XmlScanner {

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

    private final Map<String, Map<File, Set<String>>> layoutToIds = new LinkedHashMap<>();
    private final Map<File, Location> fileToLocation = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        File file = context.file;
        String layoutName = file.getName();

        Set<String> ids = new HashSet<>();
        Element root = document.getDocumentElement();
        if (root != null) {
            collectIds(root, ids);
            Location location = context.getLocation(root);
            fileToLocation.put(file, location);
        }

        layoutToIds.computeIfAbsent(layoutName, k -> new LinkedHashMap<>()).put(file, ids);
    }

    private void collectIds(Node node, Set<String> ids) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)) {
                String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
                if (id != null && !id.isEmpty()) {
                    int slash = id.indexOf('/');
                    String cleanId = slash >= 0 ? id.substring(slash + 1) : id;
                    ids.add(cleanId);
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectIds(children.item(i), ids);
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, Map<File, Set<String>>> entry : layoutToIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<File, Set<String>> files = entry.getValue();

            if (files.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : files.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<File, Set<String>> fileEntry : files.entrySet()) {
                File file = fileEntry.getKey();
                Set<String> ids = fileEntry.getValue();

                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(ids);

                if (!missing.isEmpty()) {
                    Location location = fileToLocation.get(file);
                    if (location == null) {
                        location = Location.create(file);
                    }

                    for (String missingId : missing) {
                        List<String> sources = new ArrayList<>();
                        for (Map.Entry<File, Set<String>> otherEntry : files.entrySet()) {
                            if (otherEntry.getValue().contains(missingId)) {
                                sources.add(otherEntry.getKey().getParentFile().getName());
                            }
                        }
                        Collections.sort(sources);

                        String message = String.format(
                            "Layout %s is missing ID %s (defined in %s)",
                            layoutName, missingId, String.join(", ", sources)
                        );

                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
        layoutToIds.clear();
        fileToLocation.clear();
    }
}