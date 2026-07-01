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

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "Defining the same resource more than once in the same resource folder " +
            "is likely an error, for example attempting to add a new resource " +
            "without realizing that the name is already used.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<File, Map<String, Location>> mResources = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("resources")) {
            return;
        }

        File parentDir = context.file.getParentFile();
        if (parentDir == null) {
            return;
        }

        Map<String, Location> folderResources = mResources.get(parentDir);
        if (folderResources == null) {
            folderResources = new HashMap<>();
            mResources.put(parentDir, folderResources);
        }

        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                handleResourceElement(context, element, folderResources);
            }
            child = child.getNextSibling();
        }
    }

    private void handleResourceElement(XmlContext context, Element element, Map<String, Location> folderResources) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String tagName = element.getTagName();
        String type = tagName;
        if (tagName.equals("item")) {
            type = element.getAttribute("type");
        } else if (tagName.equals("string-array") || tagName.equals("integer-array")) {
            type = "array";
        } else if (tagName.equals("declare-styleable")) {
            type = "styleable";
        }

        if (type == null || type.isEmpty()) {
            return;
        }

        String key = type + "/" + name;
        if (folderResources.containsKey(key)) {
            Location previousLocation = folderResources.get(key);
            Location currentLocation = context.getLocation(element);
            String message = String.format("Resource `%s` has already been defined in this folder", key);
            if (previousLocation != null) {
                currentLocation.setSecondary(previousLocation, "Previous definition here");
            }
            context.report(ISSUE, element, currentLocation, message);
        } else {
            Location currentLocation = context.getLocation(element);
            folderResources.put(key, currentLocation);
        }
    }
}