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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<File, Map<String, Location>> folderToResources = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String tagName = element.getTagName();
        if ("public".equals(tagName) || "public-group".equals(tagName)) {
            return;
        }

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String type = tagName;
        if ("item".equals(tagName)) {
            String typeAttr = element.getAttribute("type");
            if (typeAttr != null && !typeAttr.isEmpty()) {
                type = typeAttr;
            }
        }

        if ("string-array".equals(type) || "integer-array".equals(type)) {
            type = "array";
        }

        String key = type + "/" + name;
        File resourceFolder = context.file.getParentFile();
        if (resourceFolder == null) {
            return;
        }

        Map<String, Location> definedResources = folderToResources.get(resourceFolder);
        if (definedResources == null) {
            definedResources = new HashMap<>();
            folderToResources.put(resourceFolder, definedResources);
        }

        if (definedResources.containsKey(key)) {
            Location originalLocation = definedResources.get(key);
            Location currentLocation = context.getLocation(element);
            
            if (originalLocation != null) {
                currentLocation.setSecondary(originalLocation);
                originalLocation.setMessage("Originally defined here");
            }

            context.report(
                    ISSUE,
                    element,
                    currentLocation,
                    String.format("Resource `%s` has already been defined in this folder", key)
            );
        } else {
            definedResources.put(key, context.getLocation(element));
        }
    }
}