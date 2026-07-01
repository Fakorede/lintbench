package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@com.android.annotations.NonNull XmlContext context) {
        // No-op
    }

    @Override
    public void visitAttribute(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Attr attribute) {
        org.w3c.dom.Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        org.w3c.dom.Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String type;
        if ("item".equals(tagName)) {
            type = element.getAttribute("type");
            if (type == null || type.isEmpty()) {
                return;
            }
        } else {
            type = tagName;
        }

        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        if (value.startsWith("@") && !value.startsWith("@null")) {
            int colon = value.indexOf(':');
            int start = (colon != -1) ? colon + 1 : 1;
            int slash = value.indexOf('/', start);
            if (slash != -1) {
                String refType = value.substring(start, slash);
                if (refType.startsWith("+")) {
                    refType = refType.substring(1);
                }

                if (!type.equals(refType)) {
                    if ("drawable".equals(type) && "color".equals(refType)) {
                        return;
                    }

                    String message = String.format(
                            "Mismatch: this resource alias is declared as type `%1$s` but points to a `%2$s` resource",
                            type, refType);
                    context.report(ISSUE, element, context.getLocation(element), message);
                }
            }
        }
    }
}