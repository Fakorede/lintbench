package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.CLASS_VIEW;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class AutofillDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or higher "
                            + "or explicitly specify that the view is not important for autofill. "
                            + "Your app can help an autofill service classify the data correctly "
                            + "by providing the meaning of each view that could be autofillable, "
                            + "such as views representing usernames, passwords, credit card fields, "
                            + "email addresses, etc.\n"
                            + "\n"
                            + "The hints can have any value, but it is recommended to use "
                            + "predefined values like 'username' for a username or "
                            + "'creditCardNumber' for a credit card number. For a list of all "
                            + "predefined autofill hint constants, see the `AUTOFILL_HINT_` "
                            + "constants in the `View` reference at "
                            + "https://developer.android.com/reference/android/view/View.html.\n"
                            + "\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. "
                            + "See https://developer.android.com/reference/android/view/View.html"
                            + "#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";
    private static final String TEXT_INPUT_EDIT_TEXT = "TextInputEditText";
    private static final String CHECKED_TEXT_VIEW = "CheckedTextView";
    private static final String TEXT_VIEW = "TextView";

    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW,
                TEXT_INPUT_EDIT_TEXT
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check if targeting API 26+
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element itself has autofillHints
        String autofillHints = element.getAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS);
        if (autofillHints != null && !autofillHints.isEmpty()) {
            return;
        }

        // Check if the element itself is marked as not important for autofill
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Check if any ancestor is marked as not important for autofill (excluding descendants)
        if (hasAncestorMarkedNotImportantForAutofill(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding `android:autofillHints` "
                        + "or marking this view explicitly with `tools:ignore=\"Autofill\"`, "
                        + "or add `android:importantForAutofill=\"no\"` if this view is not "
                        + "autofillable");
    }

    private static boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (value == null || value.isEmpty()) {
            return false;
        }
        return VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value);
    }

    private static boolean hasAncestorMarkedNotImportantForAutofill(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            String value = parentElement.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (value != null && !value.isEmpty()) {
                if (VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}