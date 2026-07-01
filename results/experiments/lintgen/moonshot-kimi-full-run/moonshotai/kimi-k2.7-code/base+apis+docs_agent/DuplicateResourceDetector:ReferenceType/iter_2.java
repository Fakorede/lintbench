package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class DuplicateResourceDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference type",
            "When you generate a resource alias, the resource you are pointing to must be "
                    + "of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_ITEM, SdkConstants.TAG_DRAWABLE);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (SdkConstants.TAG_ITEM.equals(tag)) {
            String type = element.getAttributeNS(null, SdkConstants.ATTR_TYPE);
            if (type == null || type.isEmpty()) {
                return;
            }
            checkReference(context, element, type);
        } else if (SdkConstants.TAG_DRAWABLE.equals(tag)) {
            checkReference(context, element, ResourceType.DRAWABLE.getName());
        }
    }

    private static void checkReference(XmlContext context, Element element, String expectedType) {
        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return;
        }

        // Only check resource references (@...), not theme references (?...) or literals.
        if (!value.startsWith("@") || value.startsWith("@@")) {
            return;
        }

        String actualType = getReferenceType(value);
        if (actualType == null) {
            return;
        }

        if (!expectedType.equals(actualType)) {
            String message = String.format(
                    "Expected reference of type %1$s, was %2$s", expectedType, actualType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static String getReferenceType(String value) {
        String ref = value.substring(1);

        // Strip optional creation indicator.
        if (ref.startsWith("+")) {
            ref = ref.substring(1);
        }

        // Strip optional package wildcard indicator.
        if (ref.startsWith("*")) {
            ref = ref.substring(1);
        }

        int slash = ref.indexOf('/');
        if (slash <= 0 || slash == ref.length() - 1) {
            return null;
        }

        int colon = ref.lastIndexOf(':', slash);
        String type;
        if (colon >= 0) {
            type = ref.substring(colon + 1, slash);
        } else {
            type = ref.substring(0, slash);
        }

        return type.isEmpty() ? null : type;
    }
}