package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.TEXT_VIEW;

public class AutofillDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher " +
            "or explicitly specify that the view is not important for autofill. Your app can " +
            "help an autofill service classify the data correctly by providing the meaning of " +
            "each view that could be autofillable, such as views representing usernames, " +
            "passwords, credit card fields, email addresses, etc.\n\n" +
            "The hints can have any value, but it is recommended to use predefined values like " +
            "'username' for a username or 'creditCardNumber' for a credit card number. For a " +
            "list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants " +
            "in the `View` reference at https://developer.android.com/reference/android/view/View.html.\n\n" +
            "You can mark a view unimportant for autofill by specifying an `importantForAutofill` " +
            "attribute on that view or a parent view. See " +
            "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(EDIT_TEXT, TEXT_VIEW);
    }

    @Override
    public Collection<ResourceFolderType> getApplicableFolderTypes() {
        return Collections.singletonList(ResourceFolderType.LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getMainProject().getTargetSdkVersion() < 26) {
            return;
        }

        String tag = element.getTagName();
        if (TEXT_VIEW.equals(tag)) {
            String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
            if (inputType.isEmpty() || "none".equals(inputType)) {
                return;
            }
        }

        String autofillHints = element.getAttributeNS(ANDROID_URI, "autofillHints");
        if (!autofillHints.isEmpty()) {
            return;
        }

        String importantForAutofill = element.getAttributeNS(ANDROID_URI, "importantForAutofill");
        if ("no".equals(importantForAutofill) || "noExcludeDescendants".equals(importantForAutofill)) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
                "Missing `autofillHints` attribute or `importantForAutofill` is not set to `no`");
    }
}