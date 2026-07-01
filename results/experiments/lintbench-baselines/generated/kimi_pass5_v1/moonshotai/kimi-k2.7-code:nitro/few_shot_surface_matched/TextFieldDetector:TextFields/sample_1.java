package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType",
                    "Providing an `inputType` attribute on a text field improves usability because, "
                            + "depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number). "
                            + "The lint detector also looks at the `id` of the view, and if the id "
                            + "offers a hint of the purpose of the field (for example, the id "
                            + "contains the phrase `phone` or `email`), it also ensures that the "
                            + "`inputType` contains the corresponding type attributes. If you "
                            + "really want to keep the text field generic, you can suppress this "
                            + "warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT, AUTO_COMPLETE_TEXT_VIEW, MULTI_AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputType.isEmpty()) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This text field does not specify an inputType");
            return;
        }

        // inputType="text" explicitly suppresses the check
        if ("text".equals(inputType)) {
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id.isEmpty()) {
            return;
        }

        String idName = id.substring(id.lastIndexOf('/') + 1).toLowerCase(Locale.US);
        String inputTypeLower = inputType.toLowerCase(Locale.US);

        if (idName.contains("phone") && !containsPhoneType(inputTypeLower)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The id of this text field suggests it is a phone number field, but the "
                            + "inputType does not include a phone type");
        } else if (idName.contains("email") && !inputTypeLower.contains("email")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The id of this text field suggests it is an email field, but the "
                            + "inputType does not include an email type");
        }
    }

    private static boolean containsPhoneType(String inputType) {
        for (String token : inputType.split("\\|")) {
            if (token.trim().equals("phone")) {
                return true;
            }
        }
        return false;
    }
}