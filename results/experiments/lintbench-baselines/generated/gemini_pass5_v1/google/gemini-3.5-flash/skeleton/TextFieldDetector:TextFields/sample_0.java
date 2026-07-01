package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability "
                    + "because depending on the data to be input, optimized keyboards can be shown "
                    + "to the user (such as just digits and parentheses for a phone number).\n"
                    + "\n"
                    + "The lint detector also looks at the `id` of the view, and if the id offers a "
                    + "hint of the purpose of the field (for example, the `id` contains the phrase "
                    + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                    + "the corresponding type attributes.\n"
                    + "\n"
                    + "If you really want to keep the text field generic, you can suppress this warning "
                    + "by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_ID = "id";
    private static final String ATTR_NUMERIC = "numeric";
    private static final String ATTR_PASSWORD = "password";
    private static final String ATTR_PHONE_NUMBER = "phoneNumber";
    private static final String ATTR_DIGITS = "digits";
    private static final String ATTR_EDITABLE = "editable";

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE)) {
            String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            if (id != null && !id.isEmpty()) {
                String idName = id.substring(id.lastIndexOf('/') + 1);
                String expected = getExpectedInputType(idName);
                if (expected != null) {
                    if (expected.equals("phone") && !inputType.contains("phone")) {
                        org.w3c.dom.Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
                        context.report(ISSUE, attribute, context.getLocation(attribute),
                                String.format("inputType should be `%1$s` based on the ID", expected));
                    } else if (expected.equals("textUri") && !inputType.contains("Uri")) {
                        org.w3c.dom.Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
                        context.report(ISSUE, attribute, context.getLocation(attribute),
                                String.format("inputType should be `%1$s` based on the ID", expected));
                    } else if (expected.equals("textEmailAddress") && !inputType.contains("Email")) {
                        org.w3c.dom.Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
                        context.report(ISSUE, attribute, context.getLocation(attribute),
                                String.format("inputType should be `%1$s` based on the ID", expected));
                    } else if (expected.equals("textPassword") && !inputType.contains("Password")) {
                        org.w3c.dom.Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
                        context.report(ISSUE, attribute, context.getLocation(attribute),
                                String.format("inputType should be `%1$s` based on the ID", expected));
                    }
                }
            }
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_NUMERIC)
                || element.hasAttributeNS(ANDROID_URI, ATTR_PASSWORD)
                || element.hasAttributeNS(ANDROID_URI, ATTR_PHONE_NUMBER)
                || element.hasAttributeNS(ANDROID_URI, ATTR_DIGITS)) {
            return;
        }

        String editable = element.getAttributeNS(ANDROID_URI, ATTR_EDITABLE);
        if ("false".equals(editable)) {
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        String message = "Missing `inputType` attribute";
        if (id != null && !id.isEmpty()) {
            String idName = id.substring(id.lastIndexOf('/') + 1);
            String expected = getExpectedInputType(idName);
            if (expected != null) {
                message = String.format("Missing `inputType=\"%1$s\"` attribute", expected);
            }
        }

        context.report(ISSUE, element, context.getNameLocation(element), message);
    }

    private static String getExpectedInputType(@NonNull String id) {
        String lower = id.toLowerCase(java.util.Locale.US);
        if (lower.contains("phone") || lower.contains("tel")) {
            return "phone";
        } else if (lower.contains("email")) {
            return "textEmailAddress";
        } else if (lower.contains("password") || lower.contains("passwd")) {
            return "textPassword";
        } else if (lower.contains("url") || lower.contains("uri") || lower.contains("website")) {
            return "textUri";
        }

        return null;
    }
}