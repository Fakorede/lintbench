package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;

public class AutofillDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                            + "higher or explicitly specify that the view is not important for "
                            + "autofill. Your app can help an autofill service classify the data "
                            + "correctly by providing the meaning of each view that could be "
                            + "autofillable, such as views representing usernames, passwords, "
                            + "credit card fields, email addresses, etc.\n\n"
                            + "The hints can have any value, but it is recommended to use "
                            + "predefined values like 'username' for a username or 'creditCardNumber' "
                            + "for a credit card number. For a list of all predefined autofill hint "
                            + "constants, see the `AUTOFILL_HINT_` constants in the `View` reference "
                            + "at https://developer.android.com/reference/android/view/View.html.\n\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. See "
                            + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                "EditText",
                "android.support.design.widget.TextInputEditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        com.android.sdklib.AndroidVersion targetSdk = context.getProject().getTargetSdk();
        if (targetSdk != null && targetSdk.getApiLevel() < 26) {
            return;
        }

        if (isImportantForAutofill(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `autofillHints` attribute");
        }
    }

    private boolean isImportantForAutofill(Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return false;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
            String val = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if ("no".equals(val) || "noExcludeDescendants".equals(val)) {
                return false;
            }
        }

        org.w3c.dom.Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            if (parentElement.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String val = parentElement.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
                if ("noExcludeDescendants".equals(val)) {
                    return false;
                }
            }
            parent = parentElement.getParentNode();
        }

        return true;
    }
}