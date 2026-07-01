package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_ITEM);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String typeAttr = element.getAttribute(SdkConstants.ATTR_TYPE);
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        ResourceType aliasType = ResourceType.getEnum(typeAttr);
        if (aliasType == null) {
            return;
        }

        String value = getTrimmedTextContent(element);
        if (value == null || value.isEmpty()) {
            return;
        }

        ResourceType refType = extractResourceType(value);
        if (refType != null && refType != aliasType) {
            String message = String.format(
                    "Resource type mismatch: alias is of type `%s` but references a resource of type `%s`",
                    aliasType.getName(), refType.getName());
            context.report(ISSUE, context.getLocation(element), message);
        }
    }

    private static String getTrimmedTextContent(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String text = child.getNodeValue();
                if (text != null) {
                    return text.trim();
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static ResourceType extractResourceType(String value) {
        if (value.startsWith("@") || value.startsWith("?")) {
            int start = 1;
            if (value.length() > 1 && value.charAt(1) == '+') {
                start = 2;
            }
            if (value.startsWith("android:", start)) {
                start += 8;
            }
            int slash = value.indexOf('/', start);
            if (slash != -1) {
                String typeStr = value.substring(start, slash);
                return ResourceType.getEnum(typeStr);
            }
        }
        return null;
    }
}