package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

public class AutofillDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Missing autofillHints attribute",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or higher, or explicitly specify that the view is not important for autofill. Providing autofill hints helps autofill services classify data such as usernames, passwords, credit card numbers, and email addresses correctly. You can use predefined hint constants (see `View.AUTOFILL_HINT_*`) or mark a view unimportant with the `importantForAutofill` attribute.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                com.android.SdkConstants.TAG_EDIT_TEXT,
                com.android.SdkConstants.TAG_AUTO_COMPLETE_TEXT_VIEW,
                com.android.SdkConstants.TAG_MULTI_AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (context.getProject().getTargetSdk() < 26) {
            return;
        }

        if (element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI,
                com.android.SdkConstants.ATTR_AUTOFILL_HINTS)) {
            return;
        }

        if (isMarkedNo(element.getAttributeNS(com.android.SdkConstants.ANDROID_URI,
                com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL))) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        while (parent != null && parent instanceof org.w3c.dom.Element) {
            org.w3c.dom.Element el = (org.w3c.dom.Element) parent;
            if (isMarkedNo(el.getAttributeNS(com.android.SdkConstants.ANDROID_URI,
                    com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL))) {
                return;
            }
            parent = el.getParentNode();
        }

        context.report(ISSUE, element, context.getLocation(element),
                "Missing autofillHints attribute: either specify autofillHints or mark this view (or a parent view) as not important for autofill");
    }

    private static boolean isMarkedNo(String value) {
        return com.android.SdkConstants.VALUE_NO.equals(value)
                || com.android.SdkConstants.VALUE_NO_EXCLUDE_DESCENDANTS.equals(value);
    }
}