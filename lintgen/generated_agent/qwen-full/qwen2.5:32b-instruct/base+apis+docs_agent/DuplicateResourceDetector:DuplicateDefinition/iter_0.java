package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateResources",
            "Detects duplicate resource definitions within the same folder.",
            "Defining the same resource more than once in the same resource folder is likely an error. " +
                    "This can happen when attempting to add a new resource without realizing that the name is already used.",
            Category.CORRECTNESS,
            5, 5,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, Element> resources = new HashMap<>();

    @Override
    public void visitElement(XmlContext context, Element element) {
        String resourceName = getResourceName(element);
        if (resourceName != null && resources.containsKey(resourceName)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Duplicate resource definition: " + resourceName);
        } else {
            resources.put(resourceName, element);
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        // Clear the map after processing all elements in a document to avoid false positives.
        if (element.getNodeName().equals("resources")) {
            resources.clear();
        }
    }

    private String getResourceName(Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        return nameAttr != null ? nameAttr.getValue() : null;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        // Apply to all resource folders.
        return true;
    }
}