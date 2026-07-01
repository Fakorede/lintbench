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

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                            + "higher or explicitly specify that the view is not important for "
                            + "autofill. Your app can help an autofill service classify the "
                            + "data correctly by providing the meaning of each view that could "
                            + "be autofillable, such as views representing usernames, "
                            + "passwords, credit card fields, email addresses, etc.\n\n"
                            + "The hints can have any value, but it is recommended to use "
                            + "predefined values like 'username' for a username or "
                            + "'creditCardNumber' for a credit card number. For a list of all "
                            + "predefined autofill hint constants, see the `AUTOFILL_HINT_` "
                            + "constants in the `View` reference at "
                            + "https://developer.android.com/reference/android/view/View.html.\n\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. "
                            + "See https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                "EditText",
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView",
                "android.support.design.widget.TextInputEditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(
            @com.android.annotations.NonNull XmlContext context,
            @com.android.annotations.NonNull org.w3c.dom.Element element) {
        
        if (context.getProject().getTargetSdk() < 26) {
            return;
        }

        if (element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "autofillHints")) {
            return;
        }

        org.w3c.dom.Node curr = element;
        while (curr instanceof org.w3c.dom.Element) {
            org.w3c.dom.Element currEl = (org.w3c.dom.Element) curr;
            if (currEl.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "importantForAutofill")) {
                return;
            }
            curr = curr.getParentNode();
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute"
        );
    }
}