package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or higher "
                            + "or explicitly specify that the view is not important for autofill. "
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
                    IMPLEMENTATION);

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";
    private static final String ATTR_INPUT_TYPE = "inputType";

    // Values of importantForAutofill that mean "not important"
    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "android.widget.EditText",
                "AutoCompleteTextView",
                "android.widget.AutoCompleteTextView",
                "MultiAutoCompleteTextView",
                "android.widget.MultiAutoCompleteTextView",
                "TextView",
                "android.widget.TextView"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only apply this check when targeting SDK 26+
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element has an inputType attribute - if not, it's likely not an input field
        // For TextView, we only care if it has an inputType set
        String tagName = element.getLocalName();
        if ("TextView".equals(tagName) || "android.widget.TextView".equals(tagName)) {
            Attr inputType = element.getAttributeNodeNS(ANDROID_NS, ATTR_INPUT_TYPE);
            if (inputType == null) {
                return;
            }
        }

        // Check if autofillHints is already specified
        Attr autofillHints = element.getAttributeNodeNS(ANDROID_NS, ATTR_AUTOFILL_HINTS);
        if (autofillHints != null) {
            return;
        }

        // Check if importantForAutofill is set to "no" or "noExcludeDescendants" on this element
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Check if any ancestor has importantForAutofill set to "no" or "noExcludeDescendants"
        if (hasAncestorMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Report the issue
        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding `android:autofillHints` or "
                        + "marking this view as `importantForAutofill=\"no\"`",
                createFix(context, element));
    }

    private boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        Attr importantForAutofill = element.getAttributeNodeNS(ANDROID_NS, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (importantForAutofill != null) {
            String value = importantForAutofill.getValue();
            if (VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAncestorMarkedNotImportantForAutofill(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            Attr importantForAutofill = parentElement.getAttributeNodeNS(ANDROID_NS, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (importantForAutofill != null) {
                String value = importantForAutofill.getValue();
                if (VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private LintFix createFix(@NonNull XmlContext context, @NonNull Element element) {
        LintFix.GroupBuilder fixBuilder = fix().alternatives();

        // Fix 1: Add autofillHints attribute
        LintFix addAutofillHints = fix()
                .name("Add `autofillHints` attribute")
                .set(ANDROID_NS, ATTR_AUTOFILL_HINTS, "")
                .build();

        // Fix 2: Mark as not important for autofill
        LintFix markNotImportant = fix()
                .name("Mark as `importantForAutofill=\"no\"`")
                .set(ANDROID_NS, ATTR_IMPORTANT_FOR_AUTOFILL, "no")
                .build();

        return fixBuilder.add(addAutofillHints).add(markNotImportant).build();
    }
}