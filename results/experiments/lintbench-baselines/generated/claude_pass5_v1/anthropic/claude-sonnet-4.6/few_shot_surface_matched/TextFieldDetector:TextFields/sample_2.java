package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.ATTR_PASSWORD;
import static com.android.SdkConstants.EDIT_TEXT;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

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
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if inputType is set
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String inputType = inputTypeAttr != null ? inputTypeAttr.getValue() : null;

        // Check for password attribute (older style)
        String passwordAttr = element.getAttributeNS(ANDROID_URI, ATTR_PASSWORD);
        boolean hasPassword = "true".equals(passwordAttr);

        // If there's no inputType set
        if (inputType == null || inputType.isEmpty()) {
            // Check if there's a numeric/phone/digits attribute set
            String numericAttr = element.getAttributeNS(ANDROID_URI, "numeric");
            String phoneNumberAttr = element.getAttributeNS(ANDROID_URI, "phoneNumber");
            String digitsAttr = element.getAttributeNS(ANDROID_URI, "digits");

            boolean hasOtherTypeHint = hasPassword
                    || (numericAttr != null && !numericAttr.isEmpty())
                    || (phoneNumberAttr != null && !phoneNumberAttr.isEmpty())
                    || (digitsAttr != null && !digitsAttr.isEmpty());

            if (hasOtherTypeHint) {
                // They've used older attributes; still warn about inputType for modern usage
                // but this is acceptable; we skip the missing inputType warning
                return;
            }

            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This text field does not specify an `inputType` or a `hint`");
            return;
        }

        // inputType is set — check if the id suggests a specific type that should match
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        // Normalize the id for comparison
        String idLower = id.toLowerCase();

        // Check for phone-related id
        if (idLower.contains("phone") && !idLower.contains("microphone")) {
            if (!inputType.contains("phone")) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The view's `id` (`" + id + "`) suggests this is a phone number, "
                                + "but the `inputType` does not include `phone`");
            }
            return;
        }

        // Check for email-related id
        if (idLower.contains("email")) {
            if (!inputType.contains("textEmailAddress")) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The view's `id` (`" + id + "`) suggests this is an e-mail address, "
                                + "but the `inputType` does not include `textEmailAddress`");
            }
            return;
        }

        // Check for password-related id
        if (idLower.contains("password") || idLower.contains("passwd")) {
            if (!inputType.contains("textPassword")
                    && !inputType.contains("numberPassword")) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The view's `id` (`" + id + "`) suggests this is a password field, "
                                + "but the `inputType` does not include `textPassword`");
            }
            return;
        }

        // Check for URI/URL-related id
        if (idLower.contains("uri") || idLower.contains("url") || idLower.contains("link")) {
            if (!inputType.contains("textUri")) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The view's `id` (`" + id + "`) suggests this is a URI, "
                                + "but the `inputType` does not include `textUri`");
            }
            return;
        }

        // Check for postal/zip code related id
        if (idLower.contains("postal") || idLower.contains("zip")) {
            if (!inputType.contains("textPostalAddress")) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The view's `id` (`" + id + "`) suggests this is a postal address, "
                                + "but the `inputType` does not include `textPostalAddress`");
            }
            return;
        }

        // Check for person name related id
        if (idLower.contains("name") && !idLower.contains("username")) {
            if (!inputType.contains("textPersonName")) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The view's `id` (`" + id + "`) suggests this is a person name, "
                                + "but the `inputType` does not include `textPersonName`");
            }
        }
    }
}