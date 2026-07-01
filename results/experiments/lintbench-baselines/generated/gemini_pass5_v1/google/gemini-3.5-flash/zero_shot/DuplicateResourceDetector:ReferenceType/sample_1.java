package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
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
        if (root == null || !"resources".equals(root.getTagName())) {
            return;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkResourceElement(context, (Element) child);
            }
        }
    }

    private void checkResourceElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String declaredType;
        if ("item".equals(tagName)) {
            declaredType = element.getAttribute("type");
            if (declaredType == null || declaredType.isEmpty()) {
                return;
            }
        } else {
            declaredType = tagName;
        }

        String value = element.getTextContent().trim();
        String referencedType = getReferencedType(value);
        if (referencedType == null) {
            return;
        }

        if (!declaredType.equals(referencedType)) {
            // Exception: drawable can reference color or mipmap
            if ("drawable".equals(declaredType) && ("color".equals(referencedType) || "mipmap".equals(referencedType))) {
                return;
            }
            if ("mipmap".equals(declaredType) && "drawable".equals(referencedType)) {
                return;
            }

            String message = String.format(
                    "Mismatching resource type: alias `%s` is declared as `%s` but references `%s`",
                    element.getAttribute("name"), declaredType, referencedType
            );
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    message
            );
        }
    }

    private static String getReferencedType(String value) {
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