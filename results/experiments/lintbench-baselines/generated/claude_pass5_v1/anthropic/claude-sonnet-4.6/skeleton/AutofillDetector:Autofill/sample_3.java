package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                            + "higher or explicitly specify that the view is not important for autofill. "
                            + "Your app can help an autofill service classify the data correctly by "
                            + "providing the meaning of each view that could be autofillable, such as "
                            + "views representing usernames, passwords, credit card fields, email "
                            + "addresses, etc.\n"
                            + "\n"
                            + "The hints can have any value, but it is recommended to use predefined "
                            + "values like 'username' for a username or 'creditCardNumber' for a credit "
                            + "card number. For a list of all predefined autofill hint constants, see the "
                            + "`AUTOFILL_HINT_` constants in the `View` reference at "
                            + "https://developer.android.com/reference/android/view/View.html.\n"
                            + "\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. See "
                            + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Autofill hints attribute name */
    private static final String ATTR_AUTOFILL_HINTS_LOCAL = "autofillHints";

    /** Important for autofill attribute name */
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL_LOCAL = "importantForAutofill";

    /** EditText view class */
    private static final String EDIT_TEXT = "EditText";

    /** AutoCompleteTextView class */
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";

    /** MultiAutoCompleteTextView class */
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";

    /** TextInputEditText class */
    private static final String TEXT_INPUT_EDIT_TEXT = "TextInputEditText";

    /** Values for importantForAutofill that indicate the view is not important */
    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

    @Override
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
        // Only check when targeting API 26 or higher
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element has autofillHints attribute
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS_LOCAL)) {
            return;
        }

        // Check if the element itself has importantForAutofill set to no/noExcludeDescendants
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Check if any ancestor has importantForAutofill set to noExcludeDescendants
        if (hasAncestorMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Check if the element has an inputType attribute (to filter out non-input EditTexts)
        // Only flag EditTexts that have an inputType or no inputType (default is text)
        // We report if inputType is set (to something that could be autofillable) or not set at all

        // Report the issue
        LintFix fix = LintFix.create()
                .alternatives(
                        LintFix.create()
                                .set(ANDROID_URI, ATTR_AUTOFILL_HINTS_LOCAL, "")
                                .name("Set autofillHints")
                                .build(),
                        LintFix.create()
                                .set(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL_LOCAL, "no")
                                .name("Set importantForAutofill=\"no\"")
                                .build()
                );

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding it to help autofill "
                        + "services correctly autofill this view. If the view is not meant for "
                        + "autofill, set `importantForAutofill=\"no\"`.",
                fix);
    }

    /**
     * Checks whether the given element has importantForAutofill set to a value
     * that indicates it's not important for autofill (no or noExcludeDescendants).
     */
    private static boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL_LOCAL)) {
            return false;
        }
        String value = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL_LOCAL);
        return VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value);
    }

    /**
     * Checks whether any ancestor of the given element has importantForAutofill set to
     * noExcludeDescendants, which would make this element also not important for autofill.
     */
    private static boolean hasAncestorMarkedNotImportantForAutofill(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (parentElement.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL_LOCAL)) {
                String value = parentElement.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL_LOCAL);
                if (VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}