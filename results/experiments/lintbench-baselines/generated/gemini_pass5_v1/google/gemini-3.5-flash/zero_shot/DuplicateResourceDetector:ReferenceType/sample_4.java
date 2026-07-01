package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue TYPE_MISMATCH = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be " +
            "of the same type as the alias.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "item", "dimen", "color", "string", "drawable", "bool", "integer", "array", "string-array", "integer-array", "plurals"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        String aliasType;
        if ("item".equals(tagName)) {
            aliasType = element.getAttribute("type");
            if (aliasType.isEmpty()) {
                return;
            }
        } else {
            aliasType = tagName;
        }

        NodeList children = element.getChildNodes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue();
                if (text != null) {
                    sb.append(text);
                }
            }
        }

        String value = sb.toString().trim();
        if (!value.startsWith("@") || value.startsWith("@null")) {
            return;
        }

        String targetType = getResourceType(value);
        if (targetType == null) {
            return;
        }

        boolean compatible = aliasType.equals(targetType);
        if (!compatible) {
            if ("drawable".equals(aliasType) && "color".equals(targetType)) {
                compatible = true;
            } else if ("array".equals(aliasType) && targetType.endsWith("array")) {
                compatible = true;
            }
        }

        if (!compatible) {
            String message = String.format(
                    "Mismatch: This alias is of type `%1$s` but points to `%2$s` which is a `%3$s`",
                    aliasType, value, targetType
            );
            context.report(TYPE_MISMATCH, element, context.getLocation(element), message);
        }
    }

    private static String getResourceType(String value) {
        if (!value.startsWith("@")) {
            return null;
        }
        int slash = value.indexOf('/');
        if (slash == -1) {
            return null;
        }
        int start = 1;
        if (value.startsWith("@+")) {
            start = 2;
        }
        int colon = value.indexOf(':', start);
        if (colon != -1 && colon < slash) {
            start = colon + 1;
        }
        return value.substring(start, slash);
    }
}