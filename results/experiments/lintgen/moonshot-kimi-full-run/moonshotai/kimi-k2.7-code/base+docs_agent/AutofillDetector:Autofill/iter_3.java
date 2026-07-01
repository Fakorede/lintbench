package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Locale;

public class AutofillDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or "
                    + "explicitly specify that the view is not important for autofill. Your app "
                    + "can help an autofill service classify the data correctly by providing the "
                    + "meaning of each view that could be autofillable, such as views representing "
                    + "usernames, passwords, credit card fields, email addresses, etc.<br/><br/>"
                    + "The hints can have any value, but it is recommended to use predefined "
                    + "values like 'username' for a username or 'creditCardNumber' for a credit "
                    + "card number. For a list of all predefined autofill hint constants, see the "
                    + "`AUTOFILL_HINT_` constants in the `View` reference.<br/><br/>"
                    + "You can mark a view unimportant for autofill by specifying an "
                    + "`importantForAutofill` attribute on that view or a parent view.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final int AUTOFILL_MIN_SDK = 26;

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        if (context.getMainProject().getTargetSdk() < AUTOFILL_MIN_SDK) {
            return;
        }

        if (!isAutofillable(element)) {
            return;
        }

        if (hasAutofillHints(element)) {
            return;
        }

        if (isNotImportantForAutofill(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing autofill hints: add `android:autofillHints` to this view or set "
                        + "`android:importantForAutofill` to `no` or `noExcludeDescendants` on "
                        + "this view or a parent view");
    }

    private static boolean isAutofillable(@NonNull Element element) {
        String tag = element.getTagName();
        return tag.endsWith("EditText")
                || tag.endsWith("AutoCompleteTextView")
                || tag.endsWith("SearchAutoComplete")
                || element.hasAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
    }

    private static boolean hasAutofillHints(@NonNull Element element) {
        String hints = element.getAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS);
        return !hints.trim().isEmpty();
    }

    private static boolean isNotImportantForAutofill(@NonNull Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (!value.isEmpty()) {
            int important = parseImportantForAutofill(value);
            if (important == 2 || important == 8) {
                // no or noExcludeDescendants
                return true;
            } else if (important == 1 || important == 4) {
                // yes or yesExcludeDescendants: the view itself is important
                return false;
            }
            // auto (0) or unknown: fall through to parent
        }

        Node node = element.getParentNode();
        while (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
            Element current = (Element) node;
            String parentValue = current.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (!parentValue.isEmpty()) {
                int important = parseImportantForAutofill(parentValue);
                if (important == 4 || important == 8) {
                    // yesExcludeDescendants or noExcludeDescendants: descendants are excluded
                    return true;
                }
            }
            node = current.getParentNode();
        }
        return false;
    }

    private static int parseImportantForAutofill(@NonNull String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "auto":
                return 0;
            case "yes":
                return 1;
            case "no":
                return 2;
            case "yesexcludedescendants":
                return 4;
            case "noexcludedescendants":
                return 8;
            default:
                try {
                    return Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {
                    return -1;
                }
        }
    }
}