package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.List;
import org.w3c.dom.Attr;
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
    public List<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_ITEM, SdkConstants.TAG_DRAWABLE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (SdkConstants.TAG_ITEM.equals(tag)) {
            String expectedType = element.getAttributeNS(null, SdkConstants.ATTR_TYPE);
            if (expectedType == null || expectedType.isEmpty()) {
                return;
            }

            String value = element.getTextContent();
            if (value != null) {
                String message = getMismatchMessage(expectedType, value.trim());
                if (message != null) {
                    context.report(ISSUE, element, context.getLocation(element), message);
                }
            }
        } else if (SdkConstants.TAG_DRAWABLE.equals(tag)) {
            Attr src = element.getAttributeNodeNS(
                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_SRC);
            if (src != null) {
                String message = getMismatchMessage(SdkConstants.TAG_DRAWABLE, src.getValue());
                if (message != null) {
                    context.report(ISSUE, src, context.getLocation(src), message);
                }
            }
        }
    }

    private static String getMismatchMessage(String expectedType, String reference) {
        if (reference == null || reference.isEmpty()) {
            return null;
        }

        ResourceUrl url = ResourceUrl.parse(reference, true);
        if (url == null || url.type == null || url.theme) {
            return null;
        }

        ResourceType referencedType = url.type;
        if (expectedType.equalsIgnoreCase(referencedType.getName())) {
            return null;
        }

        return String.format(
                "Mismatched resource type: expected @%1$s, was @%2$s",
                expectedType, referencedType.getName());
    }
}