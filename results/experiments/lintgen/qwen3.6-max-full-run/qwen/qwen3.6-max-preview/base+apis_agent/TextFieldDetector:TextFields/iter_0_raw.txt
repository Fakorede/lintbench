package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

public class TextFieldDetector extends Detector implements XmlScanner {
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
        "TextFields",
        "Missing inputType",
        "Providing an `inputType` attribute on a text field improves usability " +
        "because depending on the data to be input, optimized keyboards can be shown " +
        "to the user (such as just digits and parentheses for a phone number). " +
        "The lint detector also looks at the `id` of the view, and if the id offers a " +
        "hint of the purpose of the field (for example, the `id` contains the phrase " +
        "`phone` or `email`), then lint will also ensure that the `inputType` contains " +
        "the corresponding type attributes. " +
        "If you really want to keep the text field generic, you can suppress this warning " +
        "by setting `inputType=\"text\"`.",
        Category.USABILITY, 5, Severity.WARNING,
        new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
            "EditText",
            "AutoCompleteTextView",
            "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, "inputType");
        String id = element.getAttributeNS(ANDROID_URI, "id");

        String idName = "";
        if (id != null && !id.isEmpty()) {
            int slash = id.lastIndexOf('/');
            if (slash != -1 && slash < id.length() - 1) {
                idName = id.substring(slash + 1).toLowerCase(Locale.US);
            }
        }

        if (inputType == null || inputType.isEmpty()) {
            String message = "Missing `inputType` attribute";
            if (!idName.isEmpty()) {
                if (idName.contains("phone")) {
                    message += "; consider adding `inputType=\"phone\"`";
                } else if (idName.contains("email")) {
                    message += "; consider adding `inputType=\"textEmailAddress\"`";
                } else if (idName.contains("password")) {
                    message += "; consider adding `inputType=\"textPassword\"`";
                } else if (idName.contains("number")) {
                    message += "; consider adding `inputType=\"number\"`";
                }
            }
            context.report(ISSUE, element, context.getLocation(element), message);
            return;
        }

        // If inputType is explicitly set to "text", treat as generic and skip hint checks
        if ("text".equals(inputType)) {
            return;
        }

        String inputTypeLower = inputType.toLowerCase(Locale.US);
        if (idName.contains("phone") && !inputTypeLower.contains("phone")) {
            context.report(ISSUE, element, context.getLocation(element),
                "The id suggests this is a phone number field, but the inputType does not contain \"phone\"");
        } else if (idName.contains("email") && !inputTypeLower.contains("email")) {
            context.report(ISSUE, element, context.getLocation(element),
                "The id suggests this is an email field, but the inputType does not contain \"textEmailAddress\"");
        } else if (idName.contains("password") && !inputTypeLower.contains("password")) {
            context.report(ISSUE, element, context.getLocation(element),
                "The id suggests this is a password field, but the inputType does not contain \"textPassword\"");
        } else if (idName.contains("number") && !inputTypeLower.contains("number")) {
            context.report(ISSUE, element, context.getLocation(element),
                "The id suggests this is a number field, but the inputType does not contain \"number\"");
        }
    }
}