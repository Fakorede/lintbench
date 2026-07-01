package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
                    Category.CORRECTNESS,
                    10,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String expectedType;
        if ("item".equals(tagName)) {
            expectedType = element.getAttribute("type");
            if (expectedType == null || expectedType.isEmpty()) {
                return;
            }
        } else {
            expectedType = tagName;
        }

        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        if (value.startsWith("@") && !value.startsWith("@null") && !value.startsWith("@+")) {
            int slash = value.indexOf('/');
            if (slash != -1) {
                int colon = value.indexOf(':');
                int start = 1;
                if (colon != -1 && colon < slash) {
                    start = colon + 1;
                }
                String referencedType = value.substring(start, slash);

                if (expectedType.equals("drawable") && referencedType.equals("color")) {
                    return;
                }

                if (!expectedType.equals(referencedType)) {
                    String message = String.format(
                            "Mismatching types: %s is of type %s, but points to %s which is of type %s",
                            attribute.getValue(), expectedType, value, referencedType);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            }
        }
    }
}