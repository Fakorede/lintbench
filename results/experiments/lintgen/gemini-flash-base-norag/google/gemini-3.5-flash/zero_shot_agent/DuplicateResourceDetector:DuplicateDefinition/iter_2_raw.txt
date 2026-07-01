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

    private final Map<File, Map<String, Location>> mFolderToDefinitions = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void beforeCheckProject(Context context) {
        mFolderToDefinitions.clear();
    }

    @Override
    public void beforeCheckFile(Context context) {
        File file = context.file;
        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            return;
        }
        String folderName = parentFolder.getName();
        int dash = folderName.indexOf('-');
        String baseFolderName = dash == -1 ? folderName : folderName.substring(0, dash);
        ResourceFolderType folderType = ResourceFolderType.getFolderType(baseFolderName);
        if (folderType == null) {
            return;
        }

        if (folderType != ResourceFolderType.VALUES) {
            String resourceName = getBaseName(file.getName());
            String type = folderType.getName();
            String key = type + ":" + resourceName;

            Map<String, Location> definitions = mFolderToDefinitions.get(parentFolder);
            if (definitions == null) {
                definitions = new HashMap<>();
                mFolderToDefinitions.put(parentFolder, definitions);
            }

            if (definitions.containsKey(key)) {
                Location originalLocation = definitions.get(key);
                Location currentLocation = Location.create(file);
                if (originalLocation != null) {
                    originalLocation.setMessage("Original definition here");
                    currentLocation.setSecondary(originalLocation);
                }
                context.report(
                        ISSUE,
                        currentLocation,
                        String.format("\"%s\" has already been defined in this folder", resourceName)
                );
            } else {
                definitions.put(key, Location.create(file));
            }
        }
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

        Map<String, Location> definitions = mFolderToDefinitions.get(parentFolder);
        if (definitions == null) {
            definitions = new HashMap<>();
            mFolderToDefinitions.put(parentFolder, definitions);
        }

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;

            String tagName = element.getTagName();
            if ("style".equals(tagName)) {
                checkStyle(context, element);
            } else if ("declare-styleable".equals(tagName)) {
                checkStyleable(context, element);
            } else if ("plurals".equals(tagName)) {
                checkPlurals(context, element);
            }

            String name = element.getAttribute("name");
            if (name.isEmpty()) {
                continue;
            }

            String type = getResourceType(element);
            if (type == null || type.isEmpty()) {
                continue;
            }

            String normalizedName = name.replace('.', '_');
            String key = type + ":" + normalizedName;

            if (definitions.containsKey(key)) {
                Location originalLocation = definitions.get(key);
                Location currentLocation = context.getLocation(element);
                
                if (originalLocation != null) {
                    originalLocation.setMessage("Original definition here");
                    currentLocation.setSecondary(originalLocation);
                }
                
                context.report(
                        ISSUE,
                        element,
                        currentLocation,
                        String.format("\"%s\" has already been defined in this folder", name)
                );
            } else {
                definitions.put(key, context.getLocation(element));
            }
        }
    }

    private void checkStyle(XmlContext context, Element styleElement) {
        Map<String, Location> items = new HashMap<>();
        NodeList children = styleElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element itemElement = (Element) node;
            if (!"item".equals(itemElement.getTagName())) {
                continue;
            }
            String name = itemElement.getAttribute("name");
            if (name.isEmpty()) {
                continue;
            }
            if (items.containsKey(name)) {
                Location originalLocation = items.get(name);
                Location currentLocation = context.getLocation(itemElement);
                if (originalLocation != null) {
                    originalLocation.setMessage("Original definition here");
                    currentLocation.setSecondary(originalLocation);
                }
                context.report(
                        ISSUE,
                        itemElement,
                        currentLocation,
                        String.format("\"%s\" has already been defined in this style", name)
                );
            } else {
                items.put(name, context.getLocation(itemElement));
            }
        }
    }

    private void checkStyleable(XmlContext context, Element styleableElement) {
        Map<String, Location> attrs = new HashMap<>();
        NodeList children = styleableElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element attrElement = (Element) node;
            if (!"attr".equals(attrElement.getTagName())) {
                continue;
            }
            String name = attrElement.getAttribute("name");
            if (name.isEmpty()) {
                continue;
            }
            if (attrs.containsKey(name)) {
                Location originalLocation = attrs.get(name);
                Location currentLocation = context.getLocation(attrElement);
                if (originalLocation != null) {
                    originalLocation.setMessage("Original definition here");
                    currentLocation.setSecondary(originalLocation);
                }
                context.report(
                        ISSUE,
                        attrElement,
                        currentLocation,
                        String.format("\"%s\" has already been defined in this declare-styleable", name)
                );
            } else {
                attrs.put(name, context.getLocation(attrElement));
            }
        }
    }

    private void checkPlurals(XmlContext context, Element pluralsElement) {
        Map<String, Location> quantities = new HashMap<>();
        NodeList children = pluralsElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element itemElement = (Element) node;
            if (!"item".equals(itemElement.getTagName())) {
                continue;
            }
            String quantity = itemElement.getAttribute("quantity");
            if (quantity.isEmpty()) {
                continue;
            }
            if (quantities.containsKey(quantity)) {
                Location originalLocation = quantities.get(quantity);
                Location currentLocation = context.getLocation(itemElement);
                if (originalLocation != null) {
                    originalLocation.setMessage("Original definition here");
                    currentLocation.setSecondary(originalLocation);
                }
                context.report(
                        ISSUE,
                        itemElement,
                        currentLocation,
                        String.format("\"%s\" has already been defined in this plurals", quantity)
                );
            } else {
                quantities.put(quantity, context.getLocation(itemElement));
            }
        }
    }

    private static String getResourceType(Element element) {
        String tagName = element.getTagName();
        if ("item".equals(tagName)) {
            return element.getAttribute("type");
        } else if ("string-array".equals(tagName) || "integer-array".equals(tagName) || "array".equals(tagName)) {
            return "array";
        } else if ("declare-styleable".equals(tagName)) {
            return "styleable";
        }
        return tagName;
    }

    private static String getBaseName(String fileName) {
        int index = fileName.indexOf('.');
        if (index > 0) {
            return fileName.substring(0, index);
        }
        return fileName;
    }
}