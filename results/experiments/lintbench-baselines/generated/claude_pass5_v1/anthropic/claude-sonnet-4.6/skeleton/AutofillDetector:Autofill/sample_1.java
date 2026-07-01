package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

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

public class AutofillDetector extends LayoutDetector {

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";

    private static final String IMPORTANT_FOR_AUTOFILL_NO = "no";
    private static final String IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

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

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check if targeting SDK 26 or higher
        if (context.getProject().getTargetSdk() < 26) {
            return;
        }

        // Check if autofillHints attribute is set
        Attr autofillHintsAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS);
        if (autofillHintsAttr != null) {
            // autofillHints is specified, no issue
            return;
        }

        // Check if importantForAutofill is set to "no" or "noExcludeDescendants" on this element
        Attr importantForAutofillAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (importantForAutofillAttr != null) {
            String value = importantForAutofillAttr.getValue();
            if (IMPORTANT_FOR_AUTOFILL_NO.equals(value)
                    || IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                // Explicitly marked as not important for autofill
                return;
            }
        }

        // Check ancestors for importantForAutofill="noExcludeDescendants"
        if (hasAncestorWithImportantForAutofillNoExcludeDescendants(element)) {
            return;
        }

        // Report the issue
        LintFix fix = LintFix.create()
                .alternatives(
                        LintFix.create()
                                .set(ANDROID_URI, ATTR_AUTOFILL_HINTS, "")
                                .caretEnd()
                                .name("Set autofillHints")
                                .build(),
                        LintFix.create()
                                .set(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL, "no")
                                .name("Set importantForAutofill=\"no\"")
                                .build()
                );

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding it to help autofill "
                        + "services correctly classify the view. You can also suppress this "
                        + "warning by setting `importantForAutofill=\"no\"` on this view or "
                        + "its parent.",
                fix);
    }

    private boolean hasAncestorWithImportantForAutofillNoExcludeDescendants(@NonNull Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            Attr importantAttr = parentElement.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (importantAttr != null) {
                String value = importantAttr.getValue();
                if (IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}