package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.android.annotations.NonNull;

public class LayoutConsistencyDetector extends ResourceXmlDetector {

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
        "layout.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            LayoutConsistencyDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    private final Map<String, List<LayoutFile>> layouts = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String name = context.file.getName();
        int dot = name.lastIndexOf('.');
        String layoutName = dot >= 0 ? name.substring(0, dot) : name;

        LayoutFile layoutFile = new LayoutFile(context.file, context.getLocation(document));
        Element root = document.getDocumentElement();
        if (root != null) {
            collectIds(root, layoutFile.idToLocation, context);
        }

        List<LayoutFile> files = layouts.get(layoutName);
        if (files == null) {
            files = new ArrayList<>();
            layouts.put(layoutName, files);
        }
        files.add(layoutFile);
    }

    private void collectIds(Element element, Map<String, Location> ids, XmlContext context) {
        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            int slash = idValue.indexOf('/');
            String id = slash >= 0 ? idValue.substring(slash + 1).trim() : idValue.trim();
            if (!id.isEmpty()) {
                ids.put(id, context.getLocation(idAttr));
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids, context);
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutFile>> entry : layouts.entrySet()) {
            List<LayoutFile> files = entry.getValue();
            if (files.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutFile file : files) {
                allIds.addAll(file.idToLocation.keySet());
            }

            for (LayoutFile file : files) {
                for (String id : allIds) {
                    if (!file.idToLocation.containsKey(id)) {
                        LayoutFile sourceFile = null;
                        for (LayoutFile other : files) {
                            if (other.idToLocation.containsKey(id)) {
                                sourceFile = other;
                                break;
                            }
                        }

                        String sourceName = sourceFile != null ? sourceFile.getParentFolderName() : "another configuration";
                        String message = String.format(
                            "Layout `%s` in `%s` is missing ID `%s` (defined in `%s`)",
                            entry.getKey(),
                            file.getParentFolderName(),
                            id,
                            sourceName
                        );

                        context.report(
                            ISSUE,
                            file.fileLocation,
                            message
                        );
                    }
                }
            }
        }
        layouts.clear();
    }

    private static class LayoutFile {
        final File file;
        final Location fileLocation;
        final Map<String, Location> idToLocation = new HashMap<>();

        LayoutFile(File file, Location fileLocation) {
            this.file = file;
            this.fileLocation = fileLocation;
        }

        String getParentFolderName() {
            return file.getParentFile().getName();
        }
    }
}