package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AutofillDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or explicitly specify that the view is not important for autofill. Your app can help an autofill service classify the data correctly by providing the meaning of each view that could be autofillable, such as views representing usernames, passwords, credit card fields, email addresses, etc.\n\n" +
            "The hints can have any value, but it is recommended to use predefined values like 'username' for a username or 'creditCardNumber' for a credit card number. For a list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants in the `View` reference at https://developer.android.com/reference/android/view/View.html.\n\n" +
            "You can mark a view unimportant for autofill by specifying an `importantForAutofill` attribute on that view or a parent view. See https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
            Category.USABILITY,
            4,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getTargetSdk() < 26) {
            return;
        }

        boolean hasAutofillHints = element.hasAttributeNS(SdkConstants.ANDROID_URI, "autofillHints");
        boolean hasImportantForAutofill = element.hasAttributeNS(SdkConstants.ANDROID_URI, "importantForAutofill");

        if (!hasAutofillHints && !hasImportantForAutofill) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Specify an `autofillHints` attribute or explicitly specify that the view is not important for autofill");
        }
    }
}