package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;

public class AutofillDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                            + "higher or explicitly specify that the view is not important for "
                            + "autofill. Your app can help an autofill service classify the "
                            + "data correctly by providing the meaning of each view that "
                            + "could be autofillable, such as views representing usernames, "
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
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singleton("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getProject().getTargetSdk() < 26) {
            return;
        }

        String tagName = element.getTagName();
        boolean isEditText = tagName.equals("EditText") || tagName.endsWith(".EditText")
                || tagName.equals("android.support.design.widget.TextInputEditText")
                || tagName.equals("com.google.android.material.textfield.TextInputEditText")
                || tagName.equals("AutoCompleteTextView") || tagName.endsWith(".AutoCompleteTextView")
                || tagName.equals("MultiAutoCompleteTextView") || tagName.endsWith(".MultiAutoCompleteTextView");

        if (!isEditText) {
            return;
        }

        if (hasAutofillAttribute(element)) {
            return;
        }

        // Check if this element or any of its ancestors has importantForAutofill
        org.w3c.dom.Node curr = element;
        while (curr != null) {
            if (curr instanceof Element) {
                Element currEl = (Element) curr;
                if (hasImportantForAutofillAttribute(currEl)) {
                    return;
                }
            }
            curr = curr.getParentNode();
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute");
    }

    private boolean hasAutofillAttribute(Element element) {
        return element.hasAttributeNS(ANDROID_URI, "autofillHints")
                || element.hasAttribute("android:autofillHints");
    }

    private boolean hasImportantForAutofillAttribute(Element element) {
        return element.hasAttributeNS(ANDROID_URI, "importantForAutofill")
                || element.hasAttribute("android:importantForAutofill");
    }
}