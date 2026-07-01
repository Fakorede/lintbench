package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.CLASS_VIEW;

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

    /** Input types that are relevant for autofill */
    private static final String[] AUTOFILL_RELEVANT_INPUT_TYPES = {
        "text",
        "textAutoComplete",
        "textAutoCorrect",
        "textCapCharacters",
        "textCapSentences",
        "textCapWords",
        "textEmailAddress",
        "textEmailSubject",
        "textFilter",
        "textImeMultiLine",
        "textLongMessage",
        "textMultiLine",
        "textNoSuggestions",
        "textPassword",
        "textPersonName",
        "textPhonetic",
        "textPostalAddress",
        "textShortMessage",
        "textUri",
        "textVisiblePassword",
        "textWebEditText",
        "textWebEmailAddress",
        "textWebPassword",
        "number",
        "numberDecimal",
        "numberPassword",
        "numberSigned",
        "phone",
        "datetime",
        "date",
        "time",
    };

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "android.widget.EditText",
                "AutoCompleteTextView",
                "android.widget.AutoCompleteTextView",
                "MultiAutoCompleteTextView",
                "android.widget.MultiAutoCompleteTextView",
                "CheckedTextView",
                "android.widget.CheckedTextView",
                "Spinner",
                "android.widget.Spinner",
                "DatePicker",
                "android.widget.DatePicker",
                "TimePicker",
                "android.widget.TimePicker",
                "RadioButton",
                "android.widget.RadioButton",
                "RadioGroup",
                "android.widget.RadioGroup",
                "RatingBar",
                "android.widget.RatingBar",
                "SeekBar",
                "android.widget.SeekBar",
                "CheckBox",
                "android.widget.CheckBox",
                "Switch",
                "android.widget.Switch",
                "ToggleButton",
                "android.widget.ToggleButton"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check if targeting API 26 or higher
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if autofillHints is already specified
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if importantForAutofill is specified on this element
        if (element.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
            String value = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (isNotImportantForAutofill(value)) {
                return;
            }
        }

        // Check if any ancestor has importantForAutofill set to no/noExcludeDescendants
        if (hasAncestorWithImportantForAutofillNo(element)) {
            return;
        }

        // For EditText-like views, check if the inputType suggests it's relevant for autofill
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        boolean isEditTextLike = isEditTextLike(tagName);

        if (isEditTextLike) {
            // Check inputType - if it's not set or set to a type relevant for autofill, warn
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE)) {
                // No inputType means default text input - relevant for autofill
                reportIssue(context, element);
                return;
            }

            String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
            if (isInputTypeRelevantForAutofill(inputType)) {
                reportIssue(context, element);
            }
        } else {
            // For other view types like Spinner, DatePicker, etc., they are always relevant
            reportIssue(context, element);
        }
    }

    private boolean isEditTextLike(@NonNull String tagName) {
        return tagName.equals("EditText")
                || tagName.equals("android.widget.EditText")
                || tagName.equals("AutoCompleteTextView")
                || tagName.equals("android.widget.AutoCompleteTextView")
                || tagName.equals("MultiAutoCompleteTextView")
                || tagName.equals("android.widget.MultiAutoCompleteTextView");
    }

    private boolean isInputTypeRelevantForAutofill(@NonNull String inputType) {
        // inputType can be a combination of flags like "textPassword|textVisiblePassword"
        // Check if none of the types indicate it's not relevant
        // "none" means no input type - not relevant
        if (inputType.equals("none")) {
            return false;
        }

        // Split by | to handle multiple flags
        String[] parts = inputType.split("\\|");
        for (String part : parts) {
            part = part.trim();
            // Check against relevant types
            for (String relevantType : AUTOFILL_RELEVANT_INPUT_TYPES) {
                if (relevantType.equals(part)) {
                    return true;
                }
            }
        }

        // If the inputType starts with "text" or "number", it's likely relevant
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("text") || part.startsWith("number")
                    || part.equals("phone") || part.equals("datetime")
                    || part.equals("date") || part.equals("time")) {
                return true;
            }
        }

        return false;
    }

    private boolean isNotImportantForAutofill(@NonNull String value) {
        return value.equals("no")
                || value.equals("noExcludeDescendants");
    }

    private boolean hasAncestorWithImportantForAutofillNo(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (parentElement.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String value = parentElement.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
                if (value.equals("noExcludeDescendants")) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private void reportIssue(@NonNull XmlContext context, @NonNull Element element) {
        LintFix fix = LintFix.create()
                .group()
                .add(LintFix.create()
                        .set(ANDROID_URI, ATTR_AUTOFILL_HINTS, "")
                        .build())
                .add(LintFix.create()
                        .set(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL, "no")
                        .build())
                .build();

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute",
                fix);
    }
}