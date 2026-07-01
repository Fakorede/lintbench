package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class AutofillDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or " +
            "explicitly specify that the view is not important for autofill. " +
            "Your app can help an autofill service classify the data correctly by " +
            "providing the meaning of each view that could be autofillable, such as " +
            "views representing usernames, passwords, credit card fields, email " +
            "addresses, etc.\n\n" +
            "The hints can have any value, but it is recommended to use predefined " +
            "values like 'username' for a username or 'creditCardNumber' for a credit " +
            "card number. For a list of all predefined autofill hint constants, see the " +
            "`AUTOFILL_HINT_` constants in the `View` reference at " +
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
        // Only check if targeting SDK 26 or higher
        if (context.getProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element itself has autofillHints
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if the element itself has importantForAutofill
        if (hasImportantForAutofillAttribute(element)) {
            return;
        }

        // Check if any ancestor has importantForAutofill set to noExcludeDescendants or no
        if (ancestorHasImportantForAutofillExcluding(element)) {
            return;
        }

        // Report the issue on the element
        Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (attr != null) {
            context.report(ISSUE, element, context.getLocation(attr),
                    "Missing `autofillHints` attribute. Consider adding `android:autofillHints` " +
                    "or mark it as `importantForAutofill=\"no\"` to suppress this warning.");
        } else {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `autofillHints` attribute. Consider adding `android:autofillHints` " +
                    "or mark it as `importantForAutofill=\"no\"` to suppress this warning.");
        }
    }

    private boolean hasImportantForAutofillAttribute(Element element) {
        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL)) {
            return false;
        }
        String value = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
        // If the value is "yes" or "yesExcludeDescendants", the view is important for autofill
        // but doesn't have hints - we should still warn
        // If the value is "no", "noExcludeDescendants", or "auto" we consider it handled
        return value != null && !value.isEmpty();
    }

    private boolean ancestorHasImportantForAutofillExcluding(Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (parentElement.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String value = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
                if ("noExcludeDescendants".equals(value) || "no".equals(value)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}