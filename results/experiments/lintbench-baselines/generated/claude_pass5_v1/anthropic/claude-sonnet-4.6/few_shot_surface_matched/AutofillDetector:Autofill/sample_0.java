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
import static com.android.SdkConstants.ATTR_INPUT_TYPE;

public class AutofillDetector extends LayoutDetector implements XmlScanner {

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    private static final String VIEW_EDIT_TEXT = "EditText";
    private static final String VIEW_AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String VIEW_MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";
    private static final String VIEW_TEXT_INPUT_LAYOUT = "android.support.design.widget.TextInputLayout";
    private static final String VIEW_TEXT_INPUT_LAYOUT_ANDROIDX = "com.google.android.material.textfield.TextInputLayout";

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
                VIEW_EDIT_TEXT,
                VIEW_AUTO_COMPLETE_TEXT_VIEW,
                VIEW_MULTI_AUTO_COMPLETE_TEXT_VIEW,
                VIEW_TEXT_INPUT_LAYOUT,
                VIEW_TEXT_INPUT_LAYOUT_ANDROIDX
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check if targeting API 26 or higher
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element itself has autofillHints
        if (hasAutofillHints(element)) {
            return;
        }

        // Check if the element itself has importantForAutofill set to noExcludeDescendants or no
        if (hasImportantForAutofillNo(element)) {
            return;
        }

        // Check if any ancestor has importantForAutofill that excludes descendants
        if (ancestorHasImportantForAutofillNo(element)) {
            return;
        }

        // Report the issue
        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding it or explicitly "
                        + "specifying that the view is not important for autofill "
                        + "by specifying `android:importantForAutofill=\"no\"`.");
    }

    private boolean hasAutofillHints(@NonNull Element element) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS);
        return attr != null;
    }

    private boolean hasImportantForAutofillNo(@NonNull Element element) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (attr == null) {
            return false;
        }
        String value = attr.getValue();
        return "no".equals(value)
                || "noExcludeDescendants".equals(value);
    }

    private boolean ancestorHasImportantForAutofillNo(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            Attr attr = parentElement.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (attr != null) {
                String value = attr.getValue();
                if ("noExcludeDescendants".equals(value)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}