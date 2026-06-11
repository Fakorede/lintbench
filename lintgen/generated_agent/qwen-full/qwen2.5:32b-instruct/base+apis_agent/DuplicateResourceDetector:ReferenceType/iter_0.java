package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateResource",
            "Incorrect reference types in resource aliases",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, true));

    private final Map<String, String> resourceTypeMap = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        Attr typeAttr = element.getAttributeNode("type");

        if (nameAttr != null && typeAttr != null) {
            String resourceName = nameAttr.getValue();
            String resourceType = typeAttr.getValue();

            resourceTypeMap.put(resourceName, resourceType);
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        Attr itemAttr = element.getAttributeNode("item");
        if (itemAttr != null) {
            String itemName = itemAttr.getValue();
            String itemType = resourceTypeMap.get(itemName);

            if (itemType == null || !element.getParentNode().getNodeName().equals(itemType)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Resource alias '" + itemName + "' should be of type '" + itemType + "'");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }
}