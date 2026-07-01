package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources"
                            + " cannot vary across configurations (except as a special case, by"
                            + " version, and except for a few specific package attributes such as"
                            + " the application title and icon).",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return Detector.ALL_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (!com.android.SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (!isResourceReference(value)) {
            return;
        }

        String localName = attribute.getLocalName();
        String ownerTag = attribute.getOwnerElement().getTagName();

        if (com.android.SdkConstants.TAG_MANIFEST.equals(ownerTag)
                && (com.android.SdkConstants.ATTR_VERSION_CODE.equals(localName)
                        || com.android.SdkConstants.ATTR_VERSION_NAME.equals(localName))) {
            return;
        }

        if (com.android.SdkConstants.TAG_APPLICATION.equals(ownerTag)
                && (com.android.SdkConstants.ATTR_LABEL.equals(localName)
                        || com.android.SdkConstants.ATTR_ICON.equals(localName))) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Manifest attributes should not reference resources that can vary across"
                        + " configurations");
    }

    private static boolean isResourceReference(String value) {
        if (value == null || !value.startsWith("@")) {
            return false;
        }
        if (value.startsWith("@android:")
                || value.equals("@null")
                || value.equals("@empty")
                || value.startsWith("@+id/")
                || value.startsWith("@id/")) {
            return false;
        }
        return true;
    }
}