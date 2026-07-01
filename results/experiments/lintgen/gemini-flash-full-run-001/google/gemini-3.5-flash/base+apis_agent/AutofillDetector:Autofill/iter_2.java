package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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

    public static final Issue ISSUE = Issue.create(
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
            Category.COMPLIANCE,
            5,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getProject().getTargetSdkVersion().getFeatureLevel() < 26) {
            return;
        }

        String tagName = element.getTagName();
        if (!isAutofillView(tagName)) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTOFILL_HINTS)) {
            return;
        }

        String important = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
        if (isNo(important) || isNoExcludeDescendants(important)) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            String parentImportant = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);
            if (isNoExcludeDescendants(parentImportant) || isYesExcludeDescendants(parentImportant)) {
                return;
            }
            parent = parent.getParentNode();
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `importantForAutofill` or `autofillHints` attribute"
        );
    }

    private boolean isNo(String value) {
        return "no".equals(value) || "2".equals(value);
    }

    private boolean isNoExcludeDescendants(String value) {
        return "noExcludeDescendants".equals(value) || "8".equals(value);
    }

    private boolean isYesExcludeDescendants(String value) {
        return "yesExcludeDescendants".equals(value) || "4".equals(value);
    }

    private boolean isAutofillView(String tagName) {
        return tagName.equals(SdkConstants.EDIT_TEXT)
                || tagName.endsWith("." + SdkConstants.EDIT_TEXT)
                || tagName.equals("AutoCompleteTextView")
                || tagName.endsWith(".AutoCompleteTextView")
                || tagName.equals("MultiAutoCompleteTextView")
                || tagName.endsWith(".MultiAutoCompleteTextView")
                || tagName.equals("TextInputEditText")
                || tagName.endsWith(".TextInputEditText");
    }
}