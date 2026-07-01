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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class AutofillDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or " +
            "explicitly specify that the view is not important for autofill. Your app can help " +
            "an autofill service classify the data correctly by providing the meaning of each " +
            "view that could be autofillable, such as views representing usernames, passwords, " +
            "credit card fields, email addresses, etc.\n\n" +
            "The hints can have any value, but it is recommended to use predefined values like " +
            "'username' for a username or 'creditCardNumber' for a credit card number. For a " +
            "list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants " +
            "in the `View` reference at " +
            "https://developer.android.com/reference/android/view/View.html.\n\n" +
            "You can mark a view unimportant for autofill by specifying an " +
            "`importantForAutofill` attribute on that view or a parent view. See " +
            "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/guide/topics/text/autofill.html");

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";

    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

    public AutofillDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Only check if targeting API 26 or higher
        if (context.getProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element itself has autofillHints
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if the element or any ancestor has importantForAutofill set to no/noExcludeDescendants
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Report the issue
        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `autofillHints` attribute. Consider adding `android:autofillHints` " +
                "or explicitly specify that this view is not important for autofill by " +
                "specifying `android:importantForAutofill=\"no\"`."
        );
    }

    private boolean isMarkedNotImportantForAutofill(Element element) {
        Node current = element;
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) current;
            if (el.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String value = el.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
                if (current == element) {
                    // For the element itself, "no" or "noExcludeDescendants" both work
                    if (VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                        return true;
                    }
                } else {
                    // For ancestors, only "noExcludeDescendants" applies
                    if (VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                        return true;
                    }
                }
            }
            current = current.getParentNode();
        }
        return false;
    }
}