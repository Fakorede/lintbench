package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AutofillDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Missing `importantForAutofill` or `autofillHints` attribute",
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
            new Implementation(AutofillDetector.class, Scope.LAYOUT_RESOURCE_FILES)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(XmlScanner.ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getProject().getTargetSdkVersion().getFeatureLevel() < 26) {
            return;
        }

        String tagName = element.getTagName();
        boolean isInput = tagName.equals(SdkConstants.EDIT_TEXT)
                || tagName.equals(SdkConstants.AUTO_COMPLETE_TEXT_VIEW)
                || tagName.equals(SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW)
                || tagName.endsWith("EditText");

        if (!isInput) {
            return;
        }

        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTOFILL_HINTS)
                && !element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL)) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `importantForAutofill` or `autofillHints` attribute"
            );
        }
    }
}