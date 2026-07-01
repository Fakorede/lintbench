package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";
    private static final String TAG_EDIT_TEXT = "EditText";
    private static final String TAG_MATERIAL_TEXT_INPUT_EDIT_TEXT =
            "com.google.android.material.textfield.TextInputEditText";
    private static final String TAG_SUPPORT_TEXT_INPUT_EDIT_TEXT =
            "android.support.design.widget.TextInputEditText";

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
                    5,
                    Severity.WARNING,
                    new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_EDIT_TEXT,
                TAG_MATERIAL_TEXT_INPUT_EDIT_TEXT,
                TAG_SUPPORT_TEXT_INPUT_EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        if (isUnimportantForAutofill(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `autofillHints` attribute");
    }

    private static boolean isUnimportantForAutofill(Element element) {
        boolean isViewItself = true;
        while (element != null) {
            String value = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (!value.isEmpty()) {
                switch (value) {
                    case "no":
                    case "noExcludeDescendants":
                        return true;
                    case "yes":
                        return false;
                    case "yesExcludeDescendants":
                        return !isViewItself;
                    case "auto":
                    default:
                        break;
                }
            }
            isViewItself = false;
            Node parent = element.getParentNode();
            element = parent instanceof Element ? (Element) parent : null;
        }
        return false;
    }
}