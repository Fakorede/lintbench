package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String TAG_RESOURCES = "resources";
    private static final String TAG_ITEM = "item";
    private static final String ATTR_TYPE = "type";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "anim",
                "animator",
                "array",
                "attr",
                "bool",
                "color",
                "dimen",
                "drawable",
                "font",
                "id",
                "integer",
                "integer-array",
                "item",
                "layout",
                "menu",
                "mipmap",
                "navigation",
                "plurals",
                "raw",
                "string",
                "string-array",
                "style",
                "transition",
                "xml"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent == null || !(parent instanceof Element)) {
            return;
        }
        if (!TAG_RESOURCES.equals(((Element) parent).getTagName())) {
            return;
        }

        if (!isAlias(element)) {
            return;
        }

        String aliasType = getAliasType(element);
        if (aliasType == null) {
            return;
        }

        String reference = element.getTextContent().trim();
        String referenceType = getReferenceType(reference);
        if (referenceType == null) {
            return;
        }

        if (!aliasType.equals(referenceType)) {
            Location location = context.getValueLocation(element);
            context.report(ISSUE, location,
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias. Expected "
                            + aliasType + " but was " + referenceType);
        }
    }

    private boolean isAlias(Element element) {
        if (hasChildElement(element)) {
            return false;
        }
        String text = element.getTextContent().trim();
        return text.startsWith("@") && text.contains("/");
    }

    private boolean hasChildElement(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    private String getAliasType(Element element) {
        String tag = element.getTagName();
        if (TAG_ITEM.equals(tag)) {
            String type = element.getAttribute(ATTR_TYPE);
            return type != null && !type.isEmpty() ? type : null;
        }
        return tag;
    }

    private String getReferenceType(String reference) {
        int slash = reference.indexOf('/');
        if (slash == -1) {
            return null;
        }

        String prefix = reference.substring(0, slash);

        int colon = prefix.lastIndexOf(':');
        if (colon != -1) {
            prefix = prefix.substring(colon + 1);
        }

        if (prefix.startsWith("@")) {
            prefix = prefix.substring(1);
        }
        if (prefix.startsWith("+")) {
            prefix = prefix.substring(1);
        }
        if (prefix.startsWith("*")) {
            prefix = prefix.substring(1);
        }

        return prefix;
    }
}