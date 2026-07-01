package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class AutofillDetector extends ResourceXmlDetector {

    private static final String AUTO_FILL_HINTS = "autofillHints";
    private static final String IMPORTANT_FOR_AUTO_FILL = "importantForAutofill";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final int AUTOFILL_TARGET_SDK = 26;

    private static final String EXPLANATION = "Use Autofill\n\n"
            + "Specify an `autofillHints` attribute when targeting SDK version 26 or "
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
            + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).";

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            EXPLANATION,
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @NotNull
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "AutoCompleteTextView",
                "EditText",
                "MultiAutoCompleteTextView",
                "TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (context.getMainProject().getTargetSdkVersion() < AUTOFILL_TARGET_SDK) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, AUTO_FILL_HINTS)) {
            return;
        }

        if (isUnimportantForAutofill(element)) {
            return;
        }

        context.report(ISSUE, element, context.getNameLocation(element),
                "Add an `android:autofillHints` attribute to this view, or mark it "
                        + "(or a parent view) as not important for autofill.");
    }

    private static boolean isUnimportantForAutofill(@NotNull Element element) {
        boolean self = true;
        while (element != null) {
            String value = element.getAttributeNS(ANDROID_URI, IMPORTANT_FOR_AUTO_FILL);
            if (!value.isEmpty()) {
                if (self) {
                    if (value.equals("no") || value.equals("noExcludeDescendants")) {
                        return true;
                    }
                } else {
                    if (value.equals("noExcludeDescendants") || value.equals("yesExcludeDescendants")) {
                        return true;
                    }
                }
            }

            Node parent = element.getParentNode();
            if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE) {
                break;
            }
            element = (Element) parent;
            self = false;
        }

        return false;
    }
}