package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.EXTRACT_EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.SEARCH_AUTO_COMPLETE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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

    private static final String TEXT_INPUT_EDIT_TEXT = "TextInputEditText";
    private static final String SUPPORT_TEXT_INPUT_EDIT_TEXT =
            "android.support.design.widget.TextInputEditText";
    private static final String MATERIAL_TEXT_INPUT_EDIT_TEXT =
            "com.google.android.material.textfield.TextInputEditText";

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType",
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because, depending on the data to be input, optimized keyboards "
                            + "can be shown to the user (such as just digits and parentheses for "
                            + "a phone number).\n\n"
                            + "The lint detector also looks at the `id` of the view, and if the id "
                            + "offers a hint of the purpose of the field (for example, the id "
                            + "contains the phrase `phone` or `email`), then lint will also ensure "
                            + "that the `inputType` contains the corresponding type attributes.\n\n"
                            + "If you really want to keep the text field generic, you can suppress "
                            + "this warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW,
                EXTRACT_EDIT_TEXT,
                SEARCH_AUTO_COMPLETE,
                TEXT_INPUT_EDIT_TEXT,
                SUPPORT_TEXT_INPUT_EDIT_TEXT,
                MATERIAL_TEXT_INPUT_EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputType == null || inputType.isEmpty()) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String name = id;
        int slash = id.lastIndexOf('/');
        if (slash != -1 && slash < id.length() - 1) {
            name = id.substring(slash + 1);
        }
        String lower = name.toLowerCase(Locale.ROOT);

        if (lower.contains("phone")) {
            if (!hasInputType(inputType, "phone")) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The id of this text field suggests it is a phone field, but the "
                                + "`inputType` does not include a phone type");
            }
        } else if (lower.contains("email") || lower.contains("e-mail")) {
            if (!hasInputType(
                    inputType, "textEmailAddress", "textEmailSubject", "textWebEmailAddress")) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The id of this text field suggests it is an email field, but the "
                                + "`inputType` does not include an email type");
            }
        } else if (lower.contains("password") || lower.contains("passwd")) {
            if (!hasInputType(inputType, "textPassword", "textVisiblePassword")) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The id of this text field suggests it is a password field, but the "
                                + "`inputType` does not include a password type");
            }
        }
    }

    private static boolean hasInputType(String inputType, String... types) {
        for (String token : inputType.split("\\|")) {
            String trimmed = token.trim();
            for (String type : types) {
                if (trimmed.equals(type)) {
                    return true;
                }
            }
        }
        return false;
    }
}