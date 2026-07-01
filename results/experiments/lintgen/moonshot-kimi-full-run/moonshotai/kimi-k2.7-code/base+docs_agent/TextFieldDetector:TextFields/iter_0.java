package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class TextFieldDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType",
            "Providing an `inputType` attribute on a text field improves usability because "
                    + "depending on the data to be input, optimized keyboards can be shown to the "
                    + "user (such as just digits and parentheses for a phone number).\n\n"
                    + "The lint detector also looks at the `id` of the view, and if the id offers a "
                    + "hint of the purpose of the field (for example, the id contains the phrase "
                    + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                    + "the corresponding type attributes.\n\n"
                    + "If you really want to keep the text field generic, you can suppress this warning "
                    + "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_EDIT_TEXT,
                SdkConstants.TAG_AUTO_COMPLETE_TEXT_VIEW,
                SdkConstants.TAG_MULTI_AUTO_COMPLETE_TEXT_VIEW,
                "TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);

        if (inputType.isEmpty()) {
            report(context, element, "Missing `inputType` attribute");
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        String idLower = id.toLowerCase();
        if ((idLower.contains("phone") || idLower.contains("phonenumber")) && !inputType.contains("phone")) {
            report(context, element, "Id suggests this is a phone number field, so use an inputType of `phone`");
        }

        if ((idLower.contains("email") || idLower.contains("e_mail")) && !inputType.contains("email")) {
            report(context, element, "Id suggests this is an email address field, so use an inputType of `textEmailAddress`");
        }
    }

    private static void report(XmlContext context, Element element, String message) {
        Location location = context.getLocation(element);
        context.report(ISSUE, element, location, message);
    }
}