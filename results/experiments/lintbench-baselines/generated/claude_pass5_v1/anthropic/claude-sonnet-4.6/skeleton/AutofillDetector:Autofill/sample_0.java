package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.CLASS_VIEW;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector {

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    private static final String IMPORTANT_FOR_AUTOFILL_NO = "no";
    private static final String IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";
    private static final String IMPORTANT_FOR_AUTOFILL_YES = "yes";
    private static final String IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS = "yesExcludeDescendants";
    private static final String IMPORTANT_FOR_AUTOFILL_AUTO = "auto";

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";
    private static final String TEXT_INPUT_EDIT_TEXT = "TextInputEditText";
    private static final String CHECK_BOX = "CheckBox";
    private static final String CHECKED_TEXT_VIEW = "CheckedTextView";
    private static final String RADIO_BUTTON = "RadioButton";
    private static final String SPINNER = "Spinner";
    private static final String RATING_BAR = "RatingBar";
    private static final String SEEK_BAR = "SeekBar";
    private static final String TOGGLE_BUTTON = "ToggleButton";
    private static final String SWITCH = "Switch";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or "
                            + "explicitly specify that the view is not important for autofill. "
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

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW,
                TEXT_INPUT_EDIT_TEXT,
                CHECK_BOX,
                CHECKED_TEXT_VIEW,
                RADIO_BUTTON,
                SPINNER,
                RATING_BAR,
                SEEK_BAR,
                TOGGLE_BUTTON,
                SWITCH
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check when targeting API 26 or higher
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element itself has autofillHints attribute
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if the element or any ancestor has importantForAutofill set to no/noExcludeDescendants
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Report the issue
        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding `android:autofillHints` or "
                        + "explicitly marking this view as not important for autofill by specifying "
                        + "`tools:ignore=\"Autofill\"`, or marking the view as `android:importantForAutofill=\"no\"`.");
    }

    /**
     * Check if the element or any of its ancestors have importantForAutofill set to
     * a value that means the view is not important for autofill.
     */
    private boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        Node current = element;
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element currentElement = (Element) current;
            if (currentElement.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String value = currentElement.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
                // Strip any resource reference prefix if present
                if (value.contains("/")) {
                    value = value.substring(value.lastIndexOf('/') + 1);
                }
                if (IMPORTANT_FOR_AUTOFILL_NO.equals(value)
                        || IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
                // If the current element (not an ancestor) is marked yes/auto, it's important
                if (current == element) {
                    if (IMPORTANT_FOR_AUTOFILL_YES.equals(value)
                            || IMPORTANT_FOR_AUTOFILL_AUTO.equals(value)) {
                        return false;
                    }
                } else {
                    // For ancestors, yesExcludeDescendants means descendants are not auto-filled
                    if (IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS.equals(value)) {
                        return false;
                    }
                }
            }
            current = current.getParentNode();
        }
        return false;
    }
}