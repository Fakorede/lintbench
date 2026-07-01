package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
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

    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_PASSWORD = "password";
    private static final String ATTR_PHONE_NUMBER = "phoneNumber";
    private static final String ATTR_ID = "id";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "android.widget.EditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_INPUT_TYPE);

        if (inputTypeAttr == null) {
            // Also check for password attribute (older style)
            Attr passwordAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_PASSWORD);
            if (passwordAttr != null && "true".equals(passwordAttr.getValue())) {
                // Has password attribute set, skip
                return;
            }

            Attr idAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_ID);
            String message = "This text field does not specify an `inputType` or a `hint`";

            // Check hint attribute as well
            Attr hintAttr = element.getAttributeNodeNS(ANDROID_NS, "hint");

            if (hintAttr == null) {
                context.report(ISSUE, element, context.getLocation(element), message);
            } else {
                context.report(ISSUE, element, context.getLocation(element), message);
            }
            return;
        }

        // inputType is specified; check if it matches the id hints
        String inputType = inputTypeAttr.getValue();

        Attr idAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue().toLowerCase();
        // Strip @+id/ or @id/ prefix
        int slashIndex = id.lastIndexOf('/');
        if (slashIndex != -1) {
            id = id.substring(slashIndex + 1);
        }

        String inputTypeLower = inputType.toLowerCase();

        // Check for phone-related id
        if (id.contains("phone") && !id.contains("microphone")) {
            if (!inputTypeLower.contains("phone")) {
                String message =
                        "The view's `id` (`"
                                + idAttr.getValue()
                                + "`) suggests this is a phone field; it should use `inputType=\"phone\"`";
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        message);
            }
        }

        // Check for email-related id
        if (id.contains("email")) {
            if (!inputTypeLower.contains("email")) {
                String message =
                        "The view's `id` (`"
                                + idAttr.getValue()
                                + "`) suggests this is an e-mail address field; it should use "
                                + "`inputType=\"textEmailAddress\"`";
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        message);
            }
        }

        // Check for password-related id
        if (id.contains("password")) {
            if (!inputTypeLower.contains("password")) {
                String message =
                        "The view's `id` (`"
                                + idAttr.getValue()
                                + "`) suggests this is a password field; it should use "
                                + "`inputType=\"textPassword\"`";
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        message);
            }
        }

        // Check for URI/URL-related id
        if (id.contains("url") || id.contains("uri") || id.contains("website") || id.contains("link")) {
            if (!inputTypeLower.contains("texturi")) {
                String message =
                        "The view's `id` (`"
                                + idAttr.getValue()
                                + "`) suggests this is a URI field; it should use "
                                + "`inputType=\"textUri\"`";
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        message);
            }
        }

        // Check for postal code related id
        if (id.contains("postal") || id.contains("zip")) {
            if (!inputTypeLower.contains("number") && !inputTypeLower.contains("phone")) {
                String message =
                        "The view's `id` (`"
                                + idAttr.getValue()
                                + "`) suggests this is a postal address field; it should use "
                                + "`inputType=\"number\"`";
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        message);
            }
        }

        // Check for person name related id
        if (id.contains("person") || id.equals("name") || id.endsWith("name")
                || id.startsWith("name")) {
            if (!inputTypeLower.contains("textpersonname") && !inputTypeLower.contains("text")) {
                String message =
                        "The view's `id` (`"
                                + idAttr.getValue()
                                + "`) suggests this is a name field; it should use "
                                + "`inputType=\"textPersonName\"`";
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        message);
            }
        }
    }
}