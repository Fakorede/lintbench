package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Align with Autofill",
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
            3,
            Severity.WARNING,
            new Implementation(
                    AutofillDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "android.widget.EditText",
                "AutoCompleteTextView",
                "android.widget.AutoCompleteTextView",
                "MultiAutoCompleteTextView",
                "android.widget.MultiAutoCompleteTextView",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String important = null;
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "importantForAutofill")) {
            important = element.getAttributeNS(SdkConstants.ANDROID_URI, "importantForAutofill");
        }

        int targetSdk = context.getProject().getTargetSdk();
        if (targetSdk < 26 && important == null) {
            return;
        }

        boolean hasAutofillHints = element.hasAttributeNS(SdkConstants.ANDROID_URI, "autofillHints");
        if (hasAutofillHints) {
            return;
        }

        if (important != null) {
            if ("no".equals(important) || "noExcludeDescendants".equals(important)) {
                return;
            }
        } else {
            Node curr = element.getParentNode();
            while (curr instanceof Element) {
                Element ancestor = (Element) curr;
                if (ancestor.hasAttributeNS(SdkConstants.ANDROID_URI, "importantForAutofill")) {
                    String ancestorImportant = ancestor.getAttributeNS(SdkConstants.ANDROID_URI, "importantForAutofill");
                    if ("noExcludeDescendants".equals(ancestorImportant) || "yesExcludeDescendants".equals(ancestorImportant)) {
                        return;
                    }
                }
                curr = curr.getParentNode();
            }
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `importantForAutofill` or `autofillHints` attribute"
        );
    }
}