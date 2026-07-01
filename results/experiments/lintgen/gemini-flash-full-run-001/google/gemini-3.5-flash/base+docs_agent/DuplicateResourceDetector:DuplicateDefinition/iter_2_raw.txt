package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

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

    private final Map<File, Map<String, ResourceInfo>> folderToResources = new HashMap<>();

    private static class ResourceInfo {
        final String name;
        final Location location;

        ResourceInfo(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        folderToResources.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("resources")) {
            return;
        }

        File parentFolder = context.file.getParentFile();
        if (parentFolder == null) {
            parentFolder = new File("");
        }

        Map<String, ResourceInfo> seen = folderToResources.computeIfAbsent(parentFolder, k -> new HashMap<>());

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String tagName = element.getTagName();

            if (tagName.equals("style")) {
                checkStyle(context, element);
            } else if (tagName.equals("declare-styleable")) {
                checkDeclareStyleable(context, element);
            } else if (tagName.equals("plurals")) {
                checkPlurals(context, element);
            }

            String name = element.getAttribute("name");
            if (name.isEmpty()) {
                continue;
            }

            String type = tagName;
            if (type.equals("item")) {
                type = element.getAttribute("type");
                if (type.isEmpty()) {
                    continue;
                }
            }

            String normalized = name.replace('.', '_');
            String key = type + ":" + normalized;

            if (seen.containsKey(key)) {
                ResourceInfo original = seen.get(key);
                Location originalLocation = original.location;
                Location currentLocation = context.getNameLocation(element);
                
                originalLocation.setMessage("Original definition here");
                currentLocation.setSecondary(originalLocation);

                String msg;
                if (name.equals(original.name) && name.equals(normalized)) {
                    msg = String.format("The resource %1$s/%2$s has already been defined in this folder",
                            type, name);
                } else {
                    msg = String.format("The resource %1$s/%2$s has already been defined in this folder (as %3$s)",
                            type, normalized, original.name);
                }

                context.report(
                        ISSUE,
                        element,
                        currentLocation,
                        msg
                );
            } else {
                seen.put(key, new ResourceInfo(name, context.getNameLocation(element)));
            }
        }
    }

    private void checkStyle(XmlContext context, Element styleElement) {
        Map<String, Element> seenItems = new HashMap<>();
        NodeList children = styleElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) node;
            if (item.getTagName().equals("item")) {
                String name = item.getAttribute("name");
                if (!name.isEmpty()) {
                    if (seenItems.containsKey(name)) {
                        Element original = seenItems.get(name);
                        Location location = context.getNameLocation(item);
                        Location originalLocation = context.getNameLocation(original);
                        originalLocation.setMessage("Original definition here");
                        location.setSecondary(originalLocation);
                        context.report(
                                ISSUE,
                                item,
                                location,
                                String.format("Duplicate item %1$s in style %2$s", name, styleElement.getAttribute("name"))
                        );
                    } else {
                        seenItems.put(name, item);
                    }
                }
            }
        }
    }

    private void checkDeclareStyleable(XmlContext context, Element styleableElement) {
        Map<String, Element> seenAttrs = new HashMap<>();
        NodeList children = styleableElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element attr = (Element) node;
            if (attr.getTagName().equals("attr")) {
                String name = attr.getAttribute("name");
                if (!name.isEmpty()) {
                    if (seenAttrs.containsKey(name)) {
                        Element original = seenAttrs.get(name);
                        Location location = context.getNameLocation(attr);
                        Location originalLocation = context.getNameLocation(original);
                        originalLocation.setMessage("Original definition here");
                        location.setSecondary(originalLocation);
                        context.report(
                                ISSUE,
                                attr,
                                location,
                                String.format("Duplicate attribute %1$s in declare-styleable %2$s", name, styleableElement.getAttribute("name"))
                        );
                    } else {
                        seenAttrs.put(name, attr);
                    }
                }
            }
        }
    }

    private void checkPlurals(XmlContext context, Element pluralsElement) {
        Map<String, Element> seenQuantities = new HashMap<>();
        NodeList children = pluralsElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) node;
            if (item.getTagName().equals("item")) {
                String quantity = item.getAttribute("quantity");
                if (!quantity.isEmpty()) {
                    if (seenQuantities.containsKey(quantity)) {
                        Element original = seenQuantities.get(quantity);
                        Location location = context.getLocation(item);
                        Location originalLocation = context.getLocation(original);
                        originalLocation.setMessage("Original definition here");
                        location.setSecondary(originalLocation);
                        context.report(
                                ISSUE,
                                item,
                                location,
                                String.format("Duplicate quantity %1$s in plurals %2$s", quantity, pluralsElement.getAttribute("name"))
                        );
                    } else {
                        seenQuantities.put(quantity, item);
                    }
                }
            }
        }
    }
}