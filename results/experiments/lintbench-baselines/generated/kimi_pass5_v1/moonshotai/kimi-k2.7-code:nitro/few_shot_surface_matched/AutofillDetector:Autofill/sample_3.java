package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class AutofillDetector extends LayoutDetector implements XmlScanner {

    private static final int AUTOFILL_MIN_SDK = 26;
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Missing `autofillHints` attribute",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or "
                            + "explicitly specify that the view is not important for autofill. Your app can "
                            + "help an autofill service classify the data correctly by providing the meaning "
                            + "of each view that could be autofillable, such as views representing usernames, "
                            + "passwords, credit card fields, email addresses, etc.\n\n"
                            + "The hints can have any value, but it is recommended to use predefined values "
                            + "like 'username' for a username or 'creditCardNumber' for a credit card number. "
                            + "For a list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` "
                            + "constants in the `View` reference at "
                            + "https://developer.android.com/reference/android/view/View.html.\n\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. See "
                            + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                "AutoCompleteTextView",
                "EditText",
                "MultiAutoCompleteTextView",
                "SearchView",
                "TextView");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (context.getProject().getBuildTargetSdk() < AUTOFILL_MIN_SDK) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        while (parent instanceof org.w3c.dom.Element) {
            org.w3c.dom.Element parentElement = (org.w3c.dom.Element) parent;
            if (parentElement.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
                return;
            }
            parent = parentElement.getParentNode();
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing autofill hints: this view could be autofilled, but it does not specify "
                        + "an `autofillHints` attribute or an `importantForAutofill` attribute.");
    }
}