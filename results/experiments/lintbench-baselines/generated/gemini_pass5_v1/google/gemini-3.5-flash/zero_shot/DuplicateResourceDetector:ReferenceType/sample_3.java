package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends ResourceXmlDetector {

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

    public DuplicateResourceDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("resources".equals(tagName)) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String aliasType;
        if ("item".equals(tagName)) {
            if (element.hasAttribute("type")) {
                aliasType = element.getAttribute("type");
            } else {
                return;
            }
        } else {
            aliasType = tagName;
        }

        String value = getTextContent(element);
        if (value == null) {
            return;
        }
        value = value.trim();

        String referencedType = getReferencedType(value);
        if (referencedType != null) {
            if (!aliasType.equals(referencedType)) {
                String message = String.format(
                        "Mismatch: resource alias of type `%1$s` points to `%2$s` of type `%3$s`",
                        aliasType, value, referencedType
                );
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    @NonNull
    private static String getTextContent(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        return sb.toString();
    }

    @Nullable
    private static String getReferencedType(@NonNull String value) {
        if (!value.startsWith("@")) {
            return null;
        }
        if (value.startsWith("@+")) {
            return null;
        }
        int slash = value.indexOf('/');
        if (slash == -1) {
            return null;
        }
        int start = 1;
        int colon = value.indexOf(':');
        if (colon != -1 && colon < slash) {
            start = colon + 1;
        }
        return value.substring(start, slash);
    }
}