package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends Detector implements Detector.XmlScanner {

    private static final int AUTOFILL_MIN_SDK = 26;

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher, "
                    + "or explicitly specify that the view is not important for autofill. Your app "
                    + "can help an autofill service classify the data correctly by providing the "
                    + "meaning of each view that could be autofillable, such as views representing "
                    + "usernames, passwords, credit card fields, email addresses, etc.\n\n"
                    + "The hints can have any value, but it is recommended to use predefined "
                    + "values like 'username' for a username or 'creditCardNumber' for a credit "
                    + "card number.\n\n"
                    + "You can mark a view unimportant for autofill by specifying an "
                    + "`importantForAutofill` attribute on that view or a parent view.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getMainProject().getTargetSdk() < AUTOFILL_MIN_SDK) {
            return;
        }

        if (!isAutofillableView(element)) {
            return;
        }

        if (hasAutofillHints(element) || isMarkedNotImportant(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing autofillHints attribute: consider specifying autofillHints or marking "
                        + "the view as not important for autofill");
    }

    private static boolean isAutofillableView(Element element) {
        String tag = element.getTagName();
        return tag.endsWith("EditText")
                || tag.endsWith("AutoCompleteTextView")
                || tag.endsWith("MultiAutoCompleteTextView")
                || element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
    }

    private static boolean hasAutofillHints(Element element) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTOFILL_HINTS);
    }

    private static boolean isMarkedNotImportant(Element element) {
        if (isImportantForAutofillNo(element)) {
            return true;
        }

        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            String value =
                    parentElement.getAttributeNS(
                            SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
            if (SdkConstants.VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                return true;
            }
            parent = parent.getParentNode();
        }

        return false;
    }

    private static boolean isImportantForAutofillNo(Element element) {
        String value =
                element.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
        return SdkConstants.VALUE_NO.equals(value)
                || SdkConstants.VALUE_NO_EXCLUDE_DESCENDANTS.equals(value);
    }
}