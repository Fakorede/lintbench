package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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
                    6,
                    Severity.FATAL,
                    new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private String mFolderType;

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(Context context) {
        XmlContext xmlContext = (XmlContext) context;
        mFolderType = xmlContext.getResourceFolderType().getName();
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList("type", "name", "src", "drawable");
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String attributeName = attribute.getName();
        org.w3c.dom.Element owner = attribute.getOwnerElement();

        if ("values".equals(mFolderType)) {
            if ("type".equals(attributeName)) {
                String aliasType = attribute.getValue();
                String referenceType = getReferenceType(owner.getTextContent());
                if (referenceType != null && !referenceType.equals(aliasType)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "The alias is of type '"
                                    + aliasType
                                    + "' but it references a '"
                                    + referenceType
                                    + "' resource");
                }
            } else if ("name".equals(attributeName)) {
                String aliasType = owner.getTagName();
                if ("item".equals(aliasType)) {
                    return;
                }
                String referenceType = getReferenceType(owner.getTextContent());
                if (referenceType != null && !referenceType.equals(aliasType)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "The alias is of type '"
                                    + aliasType
                                    + "' but it references a '"
                                    + referenceType
                                    + "' resource");
                }
            }
        } else if ("drawable".equals(mFolderType)) {
            if ("src".equals(attributeName) || "drawable".equals(attributeName)) {
                String referenceType = getReferenceType(attribute.getValue());
                if (referenceType != null && !"drawable".equals(referenceType)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Expected a drawable reference but found a '"
                                    + referenceType
                                    + "' reference");
                }
            }
        }
    }

    private static String getReferenceType(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        int start;
        if (s.startsWith("@")) {
            start = 1;
            if (s.startsWith("@+") || s.startsWith("@*")) {
                start = 2;
            }
        } else if (s.startsWith("?")) {
            start = 1;
        } else {
            return null;
        }

        int slash = s.indexOf('/', start);
        if (slash == -1) {
            return null;
        }

        int colon = s.indexOf(':', start);
        if (colon != -1 && colon < slash) {
            return s.substring(colon + 1, slash);
        }
        return s.substring(start, slash);
    }
}