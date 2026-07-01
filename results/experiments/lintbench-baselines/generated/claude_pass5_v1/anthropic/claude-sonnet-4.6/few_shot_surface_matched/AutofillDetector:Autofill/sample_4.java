package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;

public class AutofillDetector extends LayoutDetector implements XmlScanner {

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or higher "
                            + "or explicitly specify that the view is not important for autofill. "
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
                    new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                "android.widget.EditText",
                "android.widget.AutoCompleteTextView",
                "androidx.appcompat.widget.AppCompatEditText",
                "android.support.v7.widget.AppCompatEditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check when targeting API 26 or higher
        int targetSdk = context.getMainProject().getTargetSdk();
        if (targetSdk < 26) {
            return;
        }

        // Check if the element itself has autofillHints attribute
        Attr autofillHints = element.getAttributeNodeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS);
        if (autofillHints != null) {
            return;
        }

        // Check if the element or any ancestor has importantForAutofill set to no/noExcludeDescendants
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding `android:autofillHints` "
                        + "or explicitly mark this view as `android:importantForAutofill=\"no\"`");
    }

    private boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        Node current = element;
        while (current instanceof Element) {
            Element el = (Element) current;
            Attr importantForAutofill = el.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (importantForAutofill != null) {
                String value = importantForAutofill.getValue();
                if (VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
                // If the current element (not an ancestor) has any importantForAutofill value,
                // stop checking ancestors for noExcludeDescendants
                if (current == element) {
                    // The element itself has the attribute but not a disabling value
                    // Continue to check parents for noExcludeDescendants
                } else {
                    // Ancestor has importantForAutofill but not a disabling one; stop
                    break;
                }
            }
            current = current.getParentNode();
        }
        return false;
    }
}