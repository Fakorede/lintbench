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

    private static final Implementation IMPLEMENTATION =
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or explicitly specify that the view is not important for autofill. Your app can help an autofill service classify the data correctly by providing the meaning of each view that could be autofillable, such as views representing usernames, passwords, credit card fields, email addresses, etc.\n\n"
                            + "The hints can have any value, but it is recommended to use predefined values like 'username' for a username or 'creditCardNumber' for a credit card number. For a list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants in the `View` reference at https://developer.android.com/reference/android/view/View.html.\n\n"
                            + "You can mark a view unimportant for autofill by specifying an `importantForAutofill` attribute on that view or a parent view. See https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                "EditText",
                "TextView",
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView",
                "SearchView");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, "autofillHints")) {
            return;
        }

        if (isUnimportant(element) || hasUnimportantAncestor(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Add an `autofillHints` attribute or mark this view as not important for autofill.");
    }

    private static boolean isUnimportant(org.w3c.dom.Element element) {
        String value = element.getAttributeNS(ANDROID_URI, "importantForAutofill");
        return "no".equals(value) || "noExcludeDescendants".equals(value);
    }

    private static boolean hasUnimportantAncestor(org.w3c.dom.Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        while (parent instanceof org.w3c.dom.Element) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) parent;
            if (isUnimportant(e)) {
                return true;
            }
            parent = e.getParentNode();
        }
        return false;
    }
}