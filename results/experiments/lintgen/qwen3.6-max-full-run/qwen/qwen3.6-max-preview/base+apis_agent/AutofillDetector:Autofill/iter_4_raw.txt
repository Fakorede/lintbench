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

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class AutofillDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "Autofill",
        "Use Autofill",
        "Specify an `autofillHints` attribute when targeting SDK version 26 or higher " +
        "or explicitly specify that the view is not important for autofill. Your app can help " +
        "an autofill service classify the data correctly by providing the meaning of each view " +
        "that could be autofillable, such as views representing usernames, passwords, credit card " +
        "fields, email addresses, etc.\n\n" +
        "The hints can have any value, but it is recommended to use predefined values like " +
        "'username' for a username or 'creditCardNumber' for a credit card number. For a list of " +
        "all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants in the `View` " +
        "reference at https://developer.android.com/reference/android/view/View.html.\n\n" +
        "You can mark a view unimportant for autofill by specifying an `importantForAutofill` " +
        "attribute on that view or a parent view. See " +
        "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
        Category.USABILITY,
        4,
        Severity.WARNING,
        new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int targetSdk = context.getProject().getTargetSdk();
        if (targetSdk > 0 && targetSdk < 26) {
            return;
        }

        String tagName = element.getTagName();
        boolean isEditText = "EditText".equals(tagName) || "android.widget.EditText".equals(tagName);
        boolean hasInputType = element.hasAttribute("android:" + SdkConstants.ATTR_INPUT_TYPE);

        if (!isEditText && !hasInputType) {
            return;
        }

        String autofillHints = element.getAttribute("android:" + SdkConstants.ATTR_AUTOFILL_HINTS);
        if (autofillHints != null && !autofillHints.isEmpty()) {
            return;
        }

        if (isExcludedFromAutofill(element)) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
            "Missing `autofillHints` attribute. Specify `autofillHints` or set `importantForAutofill` to exclude this view from autofill.");
    }

    private static boolean isExcludedFromAutofill(Element element) {
        String selfImportant = element.getAttribute("android:" + SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
        if ("no".equals(selfImportant) || "noExcludeDescendants".equals(selfImportant)) {
            return true;
        }

        Node current = element.getParentNode();
        while (current instanceof Element) {
            Element el = (Element) current;
            String parentImportant = el.getAttribute("android:" + SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
            if ("noExcludeDescendants".equals(parentImportant) || "yesExcludeDescendants".equals(parentImportant)) {
                return true;
            }
            current = el.getParentNode();
        }
        return false;
    }
}