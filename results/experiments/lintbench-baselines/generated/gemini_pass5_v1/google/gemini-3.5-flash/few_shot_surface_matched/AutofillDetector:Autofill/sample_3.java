package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.EDIT_TEXT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;

public class AutofillDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Align with Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                            + "higher or explicitly specify that the view is not important for "
                            + "autofill. Your app can help an autofill service classify the data "
                            + "correctly by providing the meaning of each view that could be "
                            + "autofillable, such as views representing usernames, passwords, "
                            + "credit card fields, email addresses, etc.\n\n"
                            + "The hints can have any value, but it is recommended to use predefined "
                            + "values like 'username' for a username or 'creditCardNumber' for a "
                            + "credit card number. For a list of all predefined autofill hint "
                            + "constants, see the `AUTOFILL_HINT_` constants in the `View` "
                            + "reference at https://developer.android.com/reference/android/view/View.html.\n\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. See "
                            + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    new Implementation(AutofillDetector.class, Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getProject().getTargetSdkVersion().getFeatureLevel() < 26) {
            return;
        }

        String tag = element.getTagName();
        if (!tag.equals(EDIT_TEXT) && !tag.endsWith("EditText")) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)
                || element.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
            return;
        }

        if (hasImportantForAutofillAncestor(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` or `importantForAutofill` attribute");
    }

    private boolean hasImportantForAutofillAncestor(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            if (parentElement.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String val = parentElement.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
                if ("noExcludeDescendants".equals(val)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}