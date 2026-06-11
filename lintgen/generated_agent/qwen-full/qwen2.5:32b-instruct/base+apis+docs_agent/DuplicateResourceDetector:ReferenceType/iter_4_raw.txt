package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, EnumSet.of(Scope.RESOURCE_FILE)));

    private final Map<String, String> resourceTypeMap = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_ITEM);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNode(SdkConstants.ATTR_NAME);
        Attr typeAttr = element.getAttributeNode(SdkConstants.ATTR_TYPE);

        if (nameAttr != null && typeAttr != null) {
            String resourceName = nameAttr.getValue();
            String resourceType = typeAttr.getValue();

            resourceTypeMap.put(resourceName, resourceType);
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (SdkConstants.ATTR_NAME.equals(attribute.getName())) {
            Element parentElement = (Element) attribute.getOwnerElement();
            String referenceName = attribute.getValue().substring(1); // Remove the leading '@'

            String parentResourceType = getResourceType(parentElement);
            String referencedResourceType = resourceTypeMap.get(referenceName);

            if (referencedResourceType != null && !parentResourceType.equals(referencedResourceType)) {
                context.report(ISSUE, attribute,
                        context.getLocation(attribute),
                        "The reference type (" + referencedResourceType +
                                ") does not match the parent element's type (" + parentResourceType + ")");
            }
        }
    }

    private String getResourceType(Element element) {
        Attr typeAttr = element.getAttributeNode(SdkConstants.ATTR_TYPE);
        return (typeAttr != null) ? typeAttr.getValue() : "";
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.VALUES.equals(folderType);
    }
}

import java.util.Collection;