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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";
    private static final String ATTR_INPUT_TYPE = "inputType";

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";
    private static final String EXTRACT_EDIT_TEXT = "ExtractEditText";

    private static final String INPUT_TYPE_TEXT = "text";
    private static final String INPUT_TYPE_TEXT_EMAIL_ADDRESS = "textEmailAddress";
    private static final String INPUT_TYPE_PHONE = "phone";
    private static final String INPUT_TYPE_TEXT_PASSWORD = "textPassword";
    private static final String INPUT_TYPE_TEXT_VISIBLE_PASSWORD = "textVisiblePassword";
    private static final String INPUT_TYPE_TEXT_WEB_PASSWORD = "textWebPassword";
    private static final String INPUT_TYPE_TEXT_URI = "textUri";
    private static final String INPUT_TYPE_NUMBER = "number";
    private static final String INPUT_TYPE_NUMBER_SIGNED = "numberSigned";
    private static final String INPUT_TYPE_NUMBER_DECIMAL = "numberDecimal";
    private static final String INPUT_TYPE_NUMBER_PASSWORD = "numberPassword";
    private static final String INPUT_TYPE_TEXT_POSTAL_ADDRESS = "textPostalAddress";
    private static final String INPUT_TYPE_TEXT_PERSON_NAME = "textPersonName";

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability because "
                            + "depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number)."
                            + "\n\n"
                            + "The lint detector also looks at the `id` of the view, and if the id "
                            + "offers a hint of the purpose of the field (for example, the `id` "
                            + "contains the phrase `phone` or `email`), then lint will also ensure "
                            + "that the `inputType` contains the corresponding type attributes."
                            + "\n\n"
                            + "If you really want to keep the text field generic, you can suppress "
                            + "this warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW,
                EXTRACT_EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputTypeNode == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        String idName = stripIdPrefix(id);
        if (idName == null) {
            return;
        }

        String inputType = inputTypeNode.getValue();
        String lowerCaseId = idName.toLowerCase(Locale.ROOT);

        if (lowerCaseId.contains("email") || lowerCaseId.contains("e-mail")) {
            if (!inputTypeContainsAny(
                    inputType,
                    INPUT_TYPE_TEXT_EMAIL_ADDRESS)) {
                reportWrongInputType(context, inputTypeNode, INPUT_TYPE_TEXT_EMAIL_ADDRESS);
            }
        } else if (lowerCaseId.contains("phone") || lowerCaseId.contains("tel")) {
            if (!inputTypeContainsAny(inputType, INPUT_TYPE_PHONE)) {
                reportWrongInputType(context, inputTypeNode, INPUT_TYPE_PHONE);
            }
        } else if (lowerCaseId.contains("password") || lowerCaseId.contains("passwd")) {
            if (!inputTypeContainsAny(
                    inputType,
                    INPUT_TYPE_TEXT_PASSWORD,
                    INPUT_TYPE_TEXT_VISIBLE_PASSWORD,
                    INPUT_TYPE_TEXT_WEB_PASSWORD)) {
                reportWrongInputType(context, inputTypeNode, INPUT_TYPE_TEXT_PASSWORD);
            }
        } else if (lowerCaseId.contains("url")
                || lowerCaseId.contains("uri")
                || lowerCaseId.contains("website")) {
            if (!inputTypeContainsAny(inputType, INPUT_TYPE_TEXT_URI)) {
                reportWrongInputType(context, inputTypeNode, INPUT_TYPE_TEXT_URI);
            }
        } else if (lowerCaseId.contains("number")
                || lowerCaseId.contains("amount")
                || lowerCaseId.contains("card")
                || lowerCaseId.contains("quantity")) {
            if (!inputTypeContainsAny(
                    inputType,
                    INPUT_TYPE_NUMBER,
                    INPUT_TYPE_NUMBER_SIGNED,
                    INPUT_TYPE_NUMBER_DECIMAL,
                    INPUT_TYPE_NUMBER_PASSWORD)) {
                reportWrongInputType(context, inputTypeNode, INPUT_TYPE_NUMBER);
            }
        } else if (lowerCaseId.contains("postal") || lowerCaseId.contains("zip")) {
            if (!inputTypeContainsAny(inputType, INPUT_TYPE_TEXT_POSTAL_ADDRESS)) {
                reportWrongInputType(context, inputTypeNode, INPUT_TYPE_TEXT_POSTAL_ADDRESS);
            }
        } else if (lowerCaseId.contains("name")) {
            if (!inputTypeContainsAny(inputType, INPUT_TYPE_TEXT_PERSON_NAME)) {
                reportWrongInputType(context, inputTypeNode, INPUT_TYPE_TEXT_PERSON_NAME);
            }
        }
    }

    private static String stripIdPrefix(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int slash = id.lastIndexOf('/');
        if (slash != -1) {
            return id.substring(slash + 1);
        }
        return id;
    }

    private static boolean inputTypeContainsAny(String inputType, String... tokens) {
        if (inputType == null) {
            return false;
        }
        for (String part : inputType.split("\\|")) {
            String trimmed = part.trim();
            for (String token : tokens) {
                if (token.equals(trimmed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void reportWrongInputType(
            @NonNull XmlContext context, @NonNull Attr inputTypeNode, @NonNull String expected) {
        context.report(
                ISSUE,
                inputTypeNode,
                context.getLocation(inputTypeNode),
                String.format(
                        Locale.ROOT,
                        "The `inputType` for this field should include `%s`",
                        expected));
    }
}