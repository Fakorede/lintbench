package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AutofillDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";
    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                            + "higher or explicitly specify that the view is not important for autofill. "
                            + "Your app can help an autofill service classify the data correctly by "
                            + "providing the meaning of each view that could be autofillable, such as "
                            + "views representing usernames, passwords, credit card fields, email "
                            + "addresses, etc.\n\n"
                            + "The hints can have any value, but it is recommended to use predefined "
                            + "values like 'username' for a username or 'creditCardNumber' for a credit "
                            + "card number. For a list of all predefined autofill hint constants, see the "
                            + "`AUTOFILL_HINT_` constants in the `View` reference at "
                            + "https://developer.android.com/reference/android/view/View.html.\n\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. See "
                            + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("EditText", "AutoCompleteTextView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getTargetSdk() < 26) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        String important = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (VALUE_NO.equals(important) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(important)) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
                "Missing `autofillHints` attribute");
    }
}