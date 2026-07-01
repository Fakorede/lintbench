package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability because, "
                            + "depending on the data to be input, optimized keyboards can be shown to the "
                            + "user (such as just digits and parentheses for a phone number).\n\n"
                            + "This detector also looks at the `id` of the view, and if the id offers a hint "
                            + "of the purpose of the field (for example, the `id` contains the phrase `phone` "
                            + "or `email`), it will also ensure that the `inputType` contains the corresponding "
                            + "type attributes.\n\n"
                            + "If you really want to keep the text field generic, you can suppress this warning "
                            + "by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_ID = "id";

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputType == null || inputType.isEmpty()) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This text field does not specify an `inputType` attribute");
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        String idName = stripIdPrefix(id).toLowerCase(Locale.US);
        if (idName.isEmpty()) {
            return;
        }

        String lowerInputType = inputType.toLowerCase(Locale.US);

        if (idName.contains("email")) {
            if (!lowerInputType.contains("email")) {
                reportInputTypeMismatch(context, element, "email address", "email");
            }
        } else if (idName.contains("phone") || idName.contains("fax")) {
            if (!lowerInputType.contains("phone")) {
                reportInputTypeMismatch(context, element, "phone number", "phone");
            }
        } else if (idName.contains("password") || idName.contains("pwd") || idName.contains("passwd")) {
            if (!lowerInputType.contains("password")) {
                reportInputTypeMismatch(context, element, "password", "password");
            }
        } else if (idName.contains("url") || idName.contains("uri") || idName.contains("website")) {
            if (!lowerInputType.contains("uri") && !lowerInputType.contains("url")) {
                reportInputTypeMismatch(context, element, "URL", "uri/url");
            }
        } else if (idName.contains("number")
                || idName.contains("amount")
                || idName.contains("qty")
                || idName.contains("quantity")) {
            if (!lowerInputType.contains("number")) {
                reportInputTypeMismatch(context, element, "number", "number");
            }
        }
    }

    private static void reportInputTypeMismatch(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String purpose,
            @NonNull String expectedToken) {
        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The id of this text field suggests it is for a "
                        + purpose
                        + ", but the `inputType` attribute does not contain '"
                        + expectedToken
                        + "'");
    }

    private static String stripIdPrefix(String id) {
        if (id == null) {
            return "";
        }
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        }
        if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }
}