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
        "You can define a resource multiple times in different resource folders; " +
        "that's how string translations are done, for example. However, defining " +
        "the same resource more than once in the same resource folder is likely " +
        "an error, for example attempting to add a new resource without realizing " +
        "that the name is already used, and so on.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(
            DuplicateResourceDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    private final Map<String, Map<String, Location>> mFolderToResources = new HashMap<>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mFolderToResources.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("resources")) {
            return;
        }

        String folderName = context.file.getParentFile().getName();
        Map<String, Location> resourcesInFolder = mFolderToResources.computeIfAbsent(folderName, k -> new HashMap<>());

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) node;
            String tag = element.getTagName();
            if (tag.equals("eat-comment") || tag.equals("skip") || tag.equals("public") || tag.equals("public-group")) {
                continue;
            }

            String name = element.getAttribute("name");
            if (name == null || name.isEmpty()) {
                continue;
            }

            String type = tag;
            if (type.equals("item")) {
                String typeAttr = element.getAttribute("type");
                if (typeAttr != null && !typeAttr.isEmpty()) {
                    type = typeAttr;
                }
            } else if (type.equals("string-array") || type.equals("integer-array")) {
                type = "array";
            }

            String key = type + ":" + name;
            if (resourcesInFolder.containsKey(key)) {
                Location originalLocation = resourcesInFolder.get(key);
                Location currentLocation = context.getNameLocation(element);
                
                if (originalLocation != null) {
                    originalLocation.setMessage("First definition here");
                    currentLocation.setSecondary(originalLocation);
                }
                
                context.report(
                    ISSUE,
                    element,
                    currentLocation,
                    "Duplicate definition of resource `" + type + "/" + name + "`"
                );
            } else {
                resourcesInFolder.put(key, context.getNameLocation(element));
            }
        }
    }
}