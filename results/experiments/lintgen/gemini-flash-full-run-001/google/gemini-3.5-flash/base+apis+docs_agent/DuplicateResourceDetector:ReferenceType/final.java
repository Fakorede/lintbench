package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be " +
            "of the same type as the alias",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("resources")) {
            return;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String expectedType;
        if (tagName.equals("item")) {
            expectedType = element.getAttribute("type");
            if (expectedType == null || expectedType.isEmpty()) {
                return;
            }
        } else {
            expectedType = tagName;
        }

        if (expectedType.equals("style") || expectedType.equals("declare-styleable") || expectedType.equals("attr")) {
            return;
        }

        String text = element.getTextContent();
        if (text == null) {
            return;
        }
        text = text.trim();
        if (text.isEmpty()) {
            return;
        }

        String actualType = getResourceType(text);
        if (actualType == null) {
            return;
        }

        if (actualType.equals("attr")) {
            return;
        }

        if (!expectedType.equals(actualType)) {
            if (expectedType.equals("drawable") && actualType.equals("color")) {
                return;
            }

            String message = String.format(
                    "Mismatch: This resource alias is declared as `%s` but points to a `%s` (`%s`)",
                    expectedType, actualType, text);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    @Nullable
    private static String getResourceType(@NonNull String value) {
        if (value.startsWith("@") && !value.startsWith("@null") && !value.startsWith("@empty")) {
            int slash = value.indexOf('/');
            if (slash != -1) {
                int colon = value.indexOf(':');
                int start = (colon != -1 && colon < slash) ? colon + 1 : 1;
                if (start < value.length() && value.charAt(start) == '+') {
                    start++;
                }
                if (start < slash) {
                    return value.substring(start, slash);
                }
            }
        } else if (value.startsWith("?")) {
            int slash = value.indexOf('/');
            if (slash != -1) {
                int colon = value.indexOf(':');
                int start = (colon != -1 && colon < slash) ? colon + 1 : 1;
                if (start < slash) {
                    return value.substring(start, slash);
                }
            } else {
                return "attr";
            }
        }
        return null;
    }
}