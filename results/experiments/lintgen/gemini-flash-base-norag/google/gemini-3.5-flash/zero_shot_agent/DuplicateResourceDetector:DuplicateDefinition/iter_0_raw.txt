package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, " +
            "defining the same resource more than once in the same resource folder " +
            "is likely an error, for example attempting to add a new resource " +
            "without realizing that the name is already used, and so on.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    // Map from "folderName:type:name" to Location of the first definition
    private final Map<String, Location> mDefinitions = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckProject(Context context) {
        mDefinitions.clear();
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"resources".equals(root.getTagName())) {
            return;
        }

        File parentFile = context.file.getParentFile();
        if (parentFile == null) {
            return;
        }
        String folderName = parentFile.getName();

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

            String key = folderName + ":" + type + ":" + name;
            if (mDefinitions.containsKey(key)) {
                Location originalLocation = mDefinitions.get(key);
                Location currentLocation = context.getLocation(element);
                
                if (originalLocation != null) {
                    originalLocation.setMessage("Original definition here");
                    currentLocation.setSecondary(originalLocation);
                }
                
                context.report(
                        ISSUE,
                        element,
                        currentLocation,
                        String.format("Resource `%s` of type `%s` has already been defined in this folder", name, type)
                );
            } else {
                mDefinitions.put(key, context.getLocation(element));
            }
        }
    }
}