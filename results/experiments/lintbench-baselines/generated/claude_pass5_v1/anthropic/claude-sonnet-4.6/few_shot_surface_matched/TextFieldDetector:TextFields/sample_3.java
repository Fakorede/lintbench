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
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
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

    public TextFieldDetector() {
    }

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
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        if (inputType == null || inputType.isEmpty()) {
            // Check if there's a hint attribute or password attribute set
            String password = element.getAttributeNS(ANDROID_URI, "password");
            if ("true".equals(password)) {
                // password attribute is set, which implies an inputType
                return;
            }

            String phoneNumber = element.getAttributeNS(ANDROID_URI, "phoneNumber");
            if ("true".equals(phoneNumber)) {
                return;
            }

            // No inputType set - report the issue
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            String message = "This text field does not specify an `inputType` or a `hint`";

            if (id != null && !id.isEmpty()) {
                String idLower = id.toLowerCase();
                // Strip common prefixes like @+id/ or @id/
                int slashIndex = idLower.lastIndexOf('/');
                if (slashIndex >= 0) {
                    idLower = idLower.substring(slashIndex + 1);
                }

                if (idLower.contains("phone") || idLower.contains("tel")) {
                    message = "This text field does not specify an `inputType` of `phone`";
                } else if (idLower.contains("email")) {
                    message = "This text field does not specify an `inputType` of `textEmailAddress`";
                } else if (idLower.contains("url") || idLower.contains("website")
                        || idLower.contains("link")) {
                    message = "This text field does not specify an `inputType` of `textUri`";
                } else if (idLower.contains("password") || idLower.contains("passwd")
                        || idLower.contains("pwd")) {
                    message = "This text field does not specify an `inputType` of `textPassword`";
                } else if (idLower.contains("postal") || idLower.contains("zip")) {
                    message = "This text field does not specify an `inputType` of `number`";
                } else if (idLower.contains("name")) {
                    message = "This text field does not specify an `inputType` of `textPersonName`";
                } else {
                    message = "This text field does not specify an `inputType`";
                }
            } else {
                message = "This text field does not specify an `inputType`";
            }

            context.report(ISSUE, element, context.getLocation(element), message);
            return;
        }

        // inputType is set; now check if the id suggests a specific type that should be set
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String idLower = id.toLowerCase();
        int slashIndex = idLower.lastIndexOf('/');
        if (slashIndex >= 0) {
            idLower = idLower.substring(slashIndex + 1);
        }

        String inputTypeLower = inputType.toLowerCase();

        if ((idLower.contains("phone") || idLower.contains("tel"))
                && !inputTypeLower.contains("phone")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The view's `id` suggests this is a phone number, but the `inputType` does "
                            + "not include `phone`");
        } else if (idLower.contains("email")
                && !inputTypeLower.contains("emailaddress")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The view's `id` suggests this is an email address, but the `inputType` "
                            + "does not include `textEmailAddress`");
        } else if ((idLower.contains("url") || idLower.contains("website")
                || idLower.contains("link"))
                && !inputTypeLower.contains("uri")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The view's `id` suggests this is a URI, but the `inputType` does not "
                            + "include `textUri`");
        } else if ((idLower.contains("password") || idLower.contains("passwd")
                || idLower.contains("pwd"))
                && !inputTypeLower.contains("password")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The view's `id` suggests this is a password field, but the `inputType` "
                            + "does not include `textPassword`");
        }
    }
}