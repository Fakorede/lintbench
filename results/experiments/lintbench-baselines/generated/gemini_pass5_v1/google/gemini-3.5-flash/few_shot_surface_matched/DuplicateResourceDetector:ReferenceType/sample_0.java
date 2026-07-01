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
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        super.beforeCheckFile(context);
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("type");
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (!"type".equals(attribute.getName())) {
            return;
        }
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null || !"item".equals(element.getTagName())) {
            return;
        }
        String text = element.getTextContent();
        if (text == null) {
            return;
        }
        text = text.trim();
        if (!text.startsWith("@")) {
            return;
        }

        int slash = text.indexOf('/');
        if (slash == -1) {
            return;
        }
        int colon = text.indexOf(':');
        int start = (colon != -1) ? colon + 1 : 1;
        if (start >= slash) {
            return;
        }

        String refType = text.substring(start, slash);
        if (refType.startsWith("+")) {
            refType = refType.substring(1);
        }
        String aliasType = attribute.getValue();

        if (!refType.equals(aliasType)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "Resource alias of type `%s` is pointing to a resource of type `%s`",
                            aliasType, refType));
        }
    }
}