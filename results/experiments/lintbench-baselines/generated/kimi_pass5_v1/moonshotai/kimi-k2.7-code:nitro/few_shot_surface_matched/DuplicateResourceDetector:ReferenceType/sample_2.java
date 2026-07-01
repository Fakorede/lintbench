package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private com.android.resources.ResourceFolderType mFolderType;

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(Context context) {
        mFolderType =
                com.android.resources.ResourceFolderType.getFolderType(
                        context.file.getParentFile().getName());
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("type");
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (mFolderType != com.android.resources.ResourceFolderType.VALUES) {
            return;
        }

        if (!"type".equals(attribute.getName())) {
            return;
        }

        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null || !"item".equals(element.getTagName())) {
            return;
        }

        String aliasType = attribute.getValue();
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        String reference = element.getTextContent();
        if (reference == null) {
            return;
        }
        reference = reference.trim();
        if (!reference.startsWith("@")) {
            return;
        }

        String referencedType = getReferenceType(reference);
        if (referencedType == null) {
            return;
        }

        if (!aliasType.equals(referencedType)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Expected reference of type @" + aliasType + "/..., but found " + reference);
        }
    }

    private static String getReferenceType(String ref) {
        int slash = ref.indexOf('/');
        if (slash <= 0) {
            return null;
        }

        String type = ref.substring(0, slash);
        if (type.startsWith("@")) {
            type = type.substring(1);
        }
        if (type.startsWith("+")) {
            type = type.substring(1);
        }
        if (type.startsWith("*")) {
            type = type.substring(1);
        }

        int colon = type.indexOf(':');
        if (colon != -1) {
            type = type.substring(colon + 1);
        }

        return type.isEmpty() ? null : type;
    }
}