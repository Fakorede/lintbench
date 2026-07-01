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

    private final Map<File, Map<String, Location>> folderToResources = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        folderToResources.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("resources")) {
            return;
        }

        File parentFolder = context.file.getParentFile();
        if (parentFolder == null) {
            return;
        }

        Map<String, Location> seen = folderToResources.computeIfAbsent(parentFolder, k -> new HashMap<>());

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
            if (type.equals("item")) {
                type = element.getAttribute("type");
                if (type.isEmpty()) {
                    continue;
                }
            }

            String key = type + ":" + name;
            if (seen.containsKey(key)) {
                Location originalLocation = seen.get(key);
                Location currentLocation = context.getNameLocation(element);
                if (originalLocation != null) {
                    originalLocation.setMessage("Original definition here");
                    currentLocation.setSecondary(originalLocation);
                }
                context.report(
                        ISSUE,
                        element,
                        currentLocation,
                        String.format("The resource `%s/%s` has already been defined in this folder", type, name)
                );
            } else {
                seen.put(key, context.getNameLocation(element));
            }
        }
    }
}