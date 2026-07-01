package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;

public class AutofillDetector extends LayoutDetector {

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    // Values that mean "not important for autofill" for the element itself
    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

    // "yes" means important for autofill - should be flagged even if ancestor says noExcludeDescendants
    private static final String VALUE_YES = "yes";
    private static final String VALUE_YES_EXCLUDE_DESCENDANTS = "yesExcludeDescendants";

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or " +
            "explicitly specify that the view is not important for autofill. Your app can help " +
            "an autofill service classify the data correctly by providing the meaning of each " +
            "view that could be autofillable, such as views representing usernames, passwords, " +
            "credit card fields, email addresses, etc.\n\n" +
            "The hints can have any value, but it is recommended to use predefined values like " +
            "'username' for a username or 'creditCardNumber' for a credit card number. For a " +
            "list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants " +
            "in the `View` reference at " +
            "https://developer.android.com/reference/android/view/View.html.\n\n" +
            "You can mark a view unimportant for autofill by specifying an " +
            "`importantForAutofill` attribute on that view or a parent view. See " +
            "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/guide/topics/text/autofill.html");

    public AutofillDetector() {
    }

    @Override
    @Nullable
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
        // Only check if targeting API 26+
        int targetSdk = context.getMainProject().getTargetSdk();
        if (targetSdk < 26) {
            return;
        }

        // For TextView, only flag if it has inputType set (making it editable)
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }
        if ("TextView".equals(tagName) || "android.widget.TextView".equals(tagName)) {
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE)) {
                return;
            }
        }

        // Check if this element has autofillHints
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if this element itself has importantForAutofill set to a "not important" value
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Check if this element has importantForAutofill="yes" or "yesExcludeDescendants"
        // which overrides ancestor's noExcludeDescendants - in this case we should still flag it
        boolean elementExplicitlyImportant = isMarkedImportantForAutofill(element);

        // Check if any ancestor has importantForAutofill set to "noExcludeDescendants"
        // but only if the element itself doesn't override with "yes"
        if (!elementExplicitlyImportant && hasAncestorMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Report the issue
        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding `android:autofillHints` " +
                "or marking this view as `importantForAutofill=\"no\"`."
        );
    }

    private boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (attr == null) {
            return false;
        }
        String value = attr.getValue();
        if (value == null) {
            return false;
        }
        // "no" or "noExcludeDescendants" on the element itself means it's not important
        return VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value);
    }

    private boolean isMarkedImportantForAutofill(@NonNull Element element) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (attr == null) {
            return false;
        }
        String value = attr.getValue();
        if (value == null) {
            return false;
        }
        return VALUE_YES.equals(value) || VALUE_YES_EXCLUDE_DESCENDANTS.equals(value);
    }

    private boolean hasAncestorMarkedNotImportantForAutofill(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            Attr attr = parentElement.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (attr != null) {
                String value = attr.getValue();
                // Only "noExcludeDescendants" on an ancestor exempts descendants
                if (VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}