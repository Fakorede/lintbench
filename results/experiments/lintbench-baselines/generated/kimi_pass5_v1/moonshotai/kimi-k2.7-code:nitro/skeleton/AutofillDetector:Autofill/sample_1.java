package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class AutofillDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_AUTOFILL_TYPE = "autofillType";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    private static final String AUTOFILL_TYPE_NONE = "none";
    private static final String IMPORTANT_FOR_AUTOFILL_NO = "no";
    private static final String IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS =
            "noExcludeDescendants";

    private static final java.util.Collection<String> APPLICABLE_ELEMENTS =
            java.util.Arrays.asList(
                    "EditText", "AutoCompleteTextView", "MultiAutoCompleteTextView");

    private static final Implementation IMPLEMENTATION =
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or higher "
                            + "or explicitly specify that the view is not important for autofill. "
                            + "Your app can help an autofill service classify the data correctly "
                            + "by providing the meaning of each view that could be autofillable, "
                            + "such as views representing usernames, passwords, credit card fields, "
                            + "email addresses, etc.\n\n"
                            + "The hints can have any value, but it is recommended to use predefined "
                            + "values like 'username' for a username or 'creditCardNumber' for a "
                            + "credit card number. For a list of all predefined autofill hint "
                            + "constants, see the `AUTOFILL_HINT_` constants in the `View` reference "
                            + "at https://developer.android.com/reference/android/view/View.html.\n\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. See "
                            + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return APPLICABLE_ELEMENTS;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        if (hasAndroidAttribute(element, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        if (AUTOFILL_TYPE_NONE.equals(getAndroidAttribute(element, ATTR_AUTOFILL_TYPE))) {
            return;
        }

        if (isMarkedUnimportant(element) || hasUnimportantAncestor(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing autofillHints attribute: provide autofill hints or mark the view (or a "
                        + "parent view) as not important for autofill");
    }

    private static boolean hasAndroidAttribute(
            org.w3c.dom.Element element, String localName) {
        return element.hasAttributeNS(ANDROID_URI, localName);
    }

    private static String getAndroidAttribute(
            org.w3c.dom.Element element, String localName) {
        return element.getAttributeNS(ANDROID_URI, localName);
    }

    private static boolean isMarkedUnimportant(org.w3c.dom.Element element) {
        String value = getAndroidAttribute(element, ATTR_IMPORTANT_FOR_AUTOFILL);
        return IMPORTANT_FOR_AUTOFILL_NO.equals(value)
                || IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS.equals(value);
    }

    private static boolean hasUnimportantAncestor(org.w3c.dom.Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element parentElement = (org.w3c.dom.Element) parent;
            if (isMarkedUnimportant(parentElement)) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}