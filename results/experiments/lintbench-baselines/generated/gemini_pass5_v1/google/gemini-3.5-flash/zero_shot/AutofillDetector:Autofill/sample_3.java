package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;

public class AutofillDetector extends LayoutDetector {

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                    + "higher or explicitly specify that the view is not important for autofill. "
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
            Category.COMPLIANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    AutofillDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getProject().getTargetSdk() != null && context.getProject().getTargetSdk().getFeatureLevel() < 26) {
            return;
        }

        String tagName = element.getTagName();
        if (!isAutofillable(tagName)) {
            return;
        }

        if (isUnimportantForAutofill(element)) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute"
        );
    }

    private boolean isAutofillable(String tagName) {
        if (tagName.equals(SdkConstants.EDIT_TEXT)
                || tagName.equals(SdkConstants.AUTO_COMPLETE_TEXT_VIEW)
                || tagName.equals(SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW)
                || tagName.equals("android.widget.EditText")
                || tagName.equals("android.widget.AutoCompleteTextView")
                || tagName.equals("android.widget.MultiAutoCompleteTextView")
                || tagName.equals("com.google.android.material.textfield.TextInputEditText")) {
            return true;
        }
        return tagName.endsWith("EditText");
    }

    private boolean isUnimportantForAutofill(Element element) {
        String selfImportant = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if ("no".equals(selfImportant) || "noExcludeDescendants".equals(selfImportant)) {
            return true;
        }

        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            String parentImportant = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if ("noExcludeDescendants".equals(parentImportant)) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}