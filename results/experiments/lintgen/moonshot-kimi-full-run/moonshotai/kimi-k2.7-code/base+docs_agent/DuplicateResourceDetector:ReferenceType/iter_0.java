package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            DuplicateResourceDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue REFERENCE_TYPE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be "
                    + "of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "drawable",
                "color",
                "dimen",
                "string",
                "integer",
                "bool",
                "id",
                "item"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String aliasType = getAliasType(element);
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        String value = getValue(element);
        if (value == null || !value.startsWith("@")) {
            return;
        }

        String referenceType = getReferenceType(value);
        if (referenceType == null) {
            return;
        }

        if (!referenceType.equals(aliasType)) {
            Location location = context.getElementLocation(element);
            context.report(
                    REFERENCE_TYPE,
                    location,
                    String.format(
                            Locale.US,
                            "Expected reference of type @%1$s/... for <%2$s> alias, "
                                    + "but found @%3$s/...",
                            aliasType,
                            element.getNodeName(),
                            referenceType));
        }
    }

    private static String getAliasType(Element element) {
        String tag = element.getNodeName();
        if ("item".equals(tag)) {
            return element.getAttribute("type");
        }
        return tag;
    }

    private static String getValue(Element element) {
        if (element.hasChildNodes()) {
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    return null;
                }
            }
        }
        String text = element.getTextContent();
        return text != null ? text.trim() : null;
    }

    private static String getReferenceType(String reference) {
        if (reference.startsWith("?")) {
            return null;
        }

        String s = reference.substring(1);
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        if (s.startsWith("*")) {
            s = s.substring(1);
        }

        int slash = s.indexOf('/');
        if (slash == -1) {
            return null;
        }

        int colon = s.indexOf(':');
        if (colon != -1 && colon < slash) {
            return s.substring(colon + 1, slash);
        }

        return s.substring(0, slash);
    }
}