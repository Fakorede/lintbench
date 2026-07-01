package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;

public class AutofillDetector extends LayoutDetector {

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
            Scope.LAYOUT_RESOURCE_FILES
        )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getProject().getTargetSdkVersion().getFeatureLevel() < 26) {
            return;
        }

        if (!isAutofillable(element)) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "autofillHints")) {
            return;
        }

        if (hasImportantForAutofill(element)) {
            return;
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "Missing `autofillHints` attribute"
        );
    }

    private static boolean isAutofillable(Element element) {
        String tagName = element.getTagName();
        if (tagName.equals("view")) {
            String clazz = element.getAttribute(SdkConstants.ATTR_CLASS);
            if (clazz != null) {
                tagName = clazz;
            }
        }

        return tagName.equals("EditText")
            || tagName.equals("AutoCompleteTextView")
            || tagName.equals("MultiAutoCompleteTextView")
            || tagName.endsWith(".EditText")
            || tagName.endsWith(".AutoCompleteTextView")
            || tagName.endsWith(".MultiAutoCompleteTextView")
            || tagName.endsWith("TextInputEditText");
    }

    private static boolean hasImportantForAutofill(Element element) {
        org.w3c.dom.Node current = element;
        while (current instanceof Element) {
            Element currEl = (Element) current;
            if (currEl.hasAttributeNS(SdkConstants.ANDROID_URI, "importantForAutofill")) {
                return true;
            }
            current = currEl.getParentNode();
        }
        return false;
    }
}