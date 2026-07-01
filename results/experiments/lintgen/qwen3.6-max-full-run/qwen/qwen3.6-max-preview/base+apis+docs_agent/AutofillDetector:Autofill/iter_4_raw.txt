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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class AutofillDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "Autofill",
        "Use Autofill",
        "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or explicitly specify that the view is not important for autofill. Your app can help an autofill service classify the data correctly by providing the meaning of each view that could be autofillable, such as views representing usernames, passwords, credit card fields, email addresses, etc.\n\nThe hints can have any value, but it is recommended to use predefined values like 'username' for a username or 'creditCardNumber' for a credit card number. For a list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants in the `View` reference at https://developer.android.com/reference/android/view/View.html.\n\nYou can mark a view unimportant for autofill by specifying an `importantForAutofill` attribute on that view or a parent view. See https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
        Category.USABILITY,
        3,
        Severity.WARNING,
        new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int targetSdk = context.getMainProject().getTargetSdkVersion().getApiLevel();
        if (targetSdk != -1 && targetSdk < 26) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTOFILL_HINTS)) {
            return;
        }

        if (isUnimportantForAutofill(element)) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
            "Missing `autofillHints` attribute");
    }

    private static boolean isUnimportantForAutofill(Element element) {
        Node current = element;
        boolean isSelf = true;
        while (current instanceof Element) {
            Element el = (Element) current;
            if (el.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String important = el.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
                if ("noExcludeDescendants".equals(important)) {
                    return true;
                }
                if (isSelf && "no".equals(important)) {
                    return true;
                }
            }
            current = el.getParentNode();
            isSelf = false;
        }
        return false;
    }
}