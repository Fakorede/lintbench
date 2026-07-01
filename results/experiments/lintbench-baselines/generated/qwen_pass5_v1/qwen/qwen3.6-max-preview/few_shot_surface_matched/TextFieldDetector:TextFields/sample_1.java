package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType",
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number).\n\n"
                            + "The lint detector also looks at the `id` of the view, and if the id offers a "
                            + "hint of the purpose of the field (for example, the `id` contains the phrase "
                            + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                            + "the corresponding type attributes.\n\n"
                            + "If you really want to keep the text field generic, you can suppress this warning "
                            + "by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.EDIT_TEXT,
                "android.widget.EditText",
                "androidx.appcompat.widget.AppCompatEditText",
                "com.google.android.material.textfield.TextInputEditText");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id.isEmpty()) {
            return;
        }

        String idName = id.substring(id.lastIndexOf('/') + 1).toLowerCase();
        String inputTypeLower = inputType.toLowerCase();

        if (idName.contains("phone") && !inputTypeLower.contains("phone")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id suggests this is a phone number field, but the inputType does not include `phone`");
        } else if (idName.contains("email") && !inputTypeLower.contains("email")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id suggests this is an email field, but the inputType does not include `textEmailAddress`");
        } else if (idName.contains("password") && !inputTypeLower.contains("password")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id suggests this is a password field, but the inputType does not include `textPassword`");
        } else if ((idName.contains("number") || idName.contains("digit")) && !inputTypeLower.contains("number")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id suggests this is a numeric field, but the inputType does not include `number`");
        }
    }
}