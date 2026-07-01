package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "Defining the same resource more than once in the same resource folder is " +
            "likely an error, for example attempting to add a new resource without " +
            "realizing that the name is already used, and so on.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<File, Map<String, Location>> folderToResources = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"resources".equals(root.getTagName())) {
            return;
        }

        File parentFolder = context.file.getParentFile();
        if (parentFolder == null) {
            return;
        }

        Map<String, Location> seenResources = folderToResources.computeIfAbsent(parentFolder, k -> new HashMap<>());

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String name = element.getAttribute("name");
            if (name.isEmpty()) {
                continue;
            }

            String type = element.getTagName();
            if ("item".equals(type)) {
                type = element.getAttribute("type");
                if (type.isEmpty()) {
                    continue;
                }
            }

            String resourceKey = type + "/" + name;

            if (seenResources.containsKey(resourceKey)) {
                Location secondaryLocation = seenResources.get(resourceKey);
                Location primaryLocation = context.getLocation(element);
                if (secondaryLocation != null) {
                    primaryLocation.setSecondary(secondaryLocation);
                    secondaryLocation.setMessage("Previously defined here");
                }
                context.report(
                        ISSUE,
                        element,
                        primaryLocation,
                        String.format("The resource `%s/%s` has already been defined in this folder", type, name)
                );
            } else {
                seenResources.put(resourceKey, context.getLocation(element));
            }
        }
    }
}