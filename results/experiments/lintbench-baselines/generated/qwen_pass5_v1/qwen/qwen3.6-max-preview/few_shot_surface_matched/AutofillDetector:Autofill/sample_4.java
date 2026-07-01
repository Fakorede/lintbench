package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class AutofillDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
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
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String TAG_EDIT_TEXT = "EditText";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getTargetSdk() < 26) {
            return;
        }

        String tag = element.getTagName();
        boolean isEditText = TAG_EDIT_TEXT.equals(tag);
        boolean hasInputType = element.hasAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        if (!isEditText && !hasInputType) {
            return;
        }

        boolean hasAutofillHints = element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS);
        boolean hasImportantForAutofill = element.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);

        if (!hasAutofillHints && !hasImportantForAutofill) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `autofillHints` attribute. Specify hints for autofill or mark "
                            + "the view as not important for autofill.");
        }
    }
}