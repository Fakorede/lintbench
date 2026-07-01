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
            "Aligns with 'Use Autofill'",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or " +
            "explicitly specify that the view is not important for autofill. Your app can help " +
            "an autofill service classify the data correctly by providing the meaning of each " +
            "view that could be autofillable, such as views representing usernames, passwords, " +
            "credit card fields, email addresses, etc.\n\n" +
            "The hints can have any value, but it is recommended to use predefined values like " +
            "'username' for a username or 'creditCardNumber' for a credit card number. For a list " +
            "of all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants in the " +
            "`View` reference at https://developer.android.com/reference/android/view/View.html.\n\n" +
            "You can mark a view unimportant for autofill by specifying an `importantForAutofill` " +
            "attribute on that view or a parent view. See " +
            "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.EDIT_TEXT,
                "android.widget.EditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getProject().getTargetSdkVersion().getFeatureLevel() < 26) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if this element or any of its ancestors has importantForAutofill specified
        Element current = element;
        while (current != null) {
            if (current.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL)) {
                return;
            }
            Node parent = current.getParentNode();
            if (parent instanceof Element) {
                current = (Element) parent;
            } else {
                current = null;
            }
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute"
        );
    }
}