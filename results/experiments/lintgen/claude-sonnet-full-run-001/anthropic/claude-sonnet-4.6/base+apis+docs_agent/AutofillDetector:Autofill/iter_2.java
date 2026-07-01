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

import org.w3c.dom.Element;
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
        if (context.getProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element has autofillHints
        if (hasAndroidAttribute(element, SdkConstants.ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if the element itself has importantForAutofill set to suppress autofill
        String importantForAutofill = getAndroidAttribute(element, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
        if (importantForAutofill != null) {
            if (VALUE_NO.equals(importantForAutofill) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(importantForAutofill)) {
                return;
            }
            // "yes", "yesExcludeDescendants", "auto" - still needs autofillHints, fall through to report
        }

        // Check if any ancestor has importantForAutofill set to noExcludeDescendants
        if (ancestorExcludesDescendants(element)) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
                "Missing `autofillHints` attribute. Consider adding `android:autofillHints` " +
                "or mark it as `importantForAutofill=\"no\"` to suppress this warning.");
    }

    private boolean hasAndroidAttribute(Element element, String attrName) {
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, attrName)) {
            return true;
        }
        // Also check without namespace (some test XML may not use namespace)
        String value = element.getAttribute("android:" + attrName);
        return value != null && !value.isEmpty();
    }

    private String getAndroidAttribute(Element element, String attrName) {
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, attrName)) {
            return element.getAttributeNS(SdkConstants.ANDROID_URI, attrName);
        }
        String value = element.getAttribute("android:" + attrName);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return null;
    }

    private boolean ancestorExcludesDescendants(Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            String value = getAndroidAttribute(parentElement, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
            if (value != null && VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}