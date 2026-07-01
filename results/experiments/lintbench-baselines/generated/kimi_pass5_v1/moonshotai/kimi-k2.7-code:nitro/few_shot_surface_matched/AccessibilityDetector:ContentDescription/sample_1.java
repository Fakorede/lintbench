package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

public class AccessibilityDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Image without contentDescription",
                    "Non-textual widgets like ImageViews and ImageButtons should use the "
                            + "`contentDescription` attribute to specify a textual description of "
                            + "the widget so that screen readers and other accessibility tools "
                            + "can adequately describe the user interface.\n"
                            + "\n"
                            + "Elements that are purely decorative and do not provide any content "
                            + "or enable a user action should not have accessibility content "
                            + "descriptions. In this case, set their `contentDescription` to "
                            + "`@null`, or, if your minSdkVersion is 16 or higher, set "
                            + "`android:importantForAccessibility` to `no`.\n"
                            + "\n"
                            + "For text fields, do not set both `hint` and `contentDescription`; "
                            + "just set `hint`.",
                    Category.A11Y,
                    5,
                    Severity.WARNING,
                    new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String IMAGE_VIEW = "ImageView";
    private static final String IMAGE_BUTTON = "ImageButton";
    private static final String EDIT_TEXT = "EditText";

    private static final String CONTENT_DESCRIPTION = "contentDescription";
    private static final String IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";
    private static final String HINT = "hint";

    private static final String VALUE_NULL = "@null";
    private static final String VALUE_NO = "no";

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON, EDIT_TEXT);
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList(
                CONTENT_DESCRIPTION, IMPORTANT_FOR_ACCESSIBILITY, HINT);
    }

    @Override
    public void visitElement(
            com.android.tools.lint.detector.api.XmlContext context,
            org.w3c.dom.Element element) {
        String tag = element.getLocalName();
        if (EDIT_TEXT.equals(tag)) {
            return;
        }

        if (IMAGE_VIEW.equals(tag) || IMAGE_BUTTON.equals(tag)) {
            String important = element.getAttributeNS(ANDROID_URI, IMPORTANT_FOR_ACCESSIBILITY);
            if (VALUE_NO.equals(important)) {
                return;
            }

            String contentDescription =
                    element.getAttributeNS(ANDROID_URI, CONTENT_DESCRIPTION);
            if (contentDescription == null || contentDescription.isEmpty()) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing `contentDescription` attribute on image");
            }
        }
    }

    @Override
    public void visitAttribute(
            com.android.tools.lint.detector.api.XmlContext context,
            org.w3c.dom.Attr attribute) {
        String name = attribute.getLocalName();
        if (!CONTENT_DESCRIPTION.equals(name)) {
            return;
        }

        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String tag = element.getLocalName();
        if (!EDIT_TEXT.equals(tag)) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, HINT)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Do not set both `hint` and `contentDescription` on a text field; "
                            + "just set `hint`");
        }
    }
}