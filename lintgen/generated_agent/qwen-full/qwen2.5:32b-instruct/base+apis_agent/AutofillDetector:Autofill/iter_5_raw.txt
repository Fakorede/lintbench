package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Detector.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AutofillDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or explicitly specify that the view is not important for autofill.",
            "Your app can help an autofill service classify the data correctly by providing the meaning of each view that could be autofillable, such as views representing usernames, passwords, credit card fields, email addresses, etc. The hints can have any value, but it is recommended to use predefined values like 'username' for a username or 'creditCardNumber' for a credit card number.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AutofillDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getProject().getApiLevel() >= 26) {
            Attr autofillHintsAttr = element.getAttributeNode(SdkConstants.ATTR_AUTOFILL_HINTS);
            Attr importantForAutofillAttr = element.getAttributeNode(SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);

            if (autofillHintsAttr == null && importantForAutofillAttr == null) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Specify an `autofillHints` attribute or explicitly specify that the view is not important for autofill.");
            }
        }
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {}

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull org.w3c.dom.Document document) {}
}