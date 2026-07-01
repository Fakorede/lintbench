package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.ATTR_PASSWORD;
import static com.android.SdkConstants.ATTR_PHONE_NUMBER;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
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

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for inputType attribute
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        // Also check for older password/phoneNumber attributes
        String phoneNumber = element.getAttributeNS(ANDROID_URI, ATTR_PHONE_NUMBER);
        String password = element.getAttributeNS(ANDROID_URI, ATTR_PASSWORD);

        if (inputTypeAttr == null) {
            // Check if phoneNumber or password attributes are set (older style)
            if ((phoneNumber != null && !phoneNumber.isEmpty())
                    || (password != null && !password.isEmpty())) {
                // Old-style attributes are set, no need to warn about missing inputType
                return;
            }

            // Report missing inputType
            LintFix fix = LintFix.create()
                    .set(ANDROID_URI, ATTR_INPUT_TYPE, "text")
                    .build();

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This text field does not specify an `inputType` or a `hint`",
                    fix);
            return;
        }

        // inputType is set; now check if it matches the id hint
        String inputType = inputTypeAttr.getValue();

        // Get the id attribute to check for hints
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        // Normalize the id for comparison (remove @+id/ or @id/ prefix)
        String idLower = id.toLowerCase();
        if (idLower.contains("@+id/")) {
            idLower = idLower.substring(idLower.indexOf("@+id/") + 5);
        } else if (idLower.contains("@id/")) {
            idLower = idLower.substring(idLower.indexOf("@id/") + 4);
        }

        String inputTypeLower = inputType.toLowerCase();

        // Check for phone-related id
        if (idContainsWord(idLower, "phone") && !idContainsWord(idLower, "microphone")) {
            if (!inputTypeLower.contains("phone")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is a phone number, "
                                + "but the `inputType` does not include `phone`");
            }
        }

        // Check for email-related id
        if (idContainsWord(idLower, "email")) {
            if (!inputTypeLower.contains("textEmailAddress")) {
                if (!inputType.contains("textEmailAddress")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getLocation(inputTypeAttr),
                            "The view's `id` (`" + id + "`) suggests this is an e-mail address, "
                                    + "but the `inputType` does not include `textEmailAddress`");
                }
            }
        }

        // Check for password-related id
        if (idContainsWord(idLower, "password")) {
            if (!inputType.contains("textPassword")
                    && !inputType.contains("numberPassword")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is a password field, "
                                + "but the `inputType` does not include `textPassword`");
            }
        }

        // Check for URI/URL-related id
        if (idContainsWord(idLower, "uri") || idContainsWord(idLower, "url")) {
            if (!inputType.contains("textUri")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is a URI, "
                                + "but the `inputType` does not include `textUri`");
            }
        }

        // Check for postal/zip code related id
        if (idContainsWord(idLower, "postal") || idContainsWord(idLower, "zip")) {
            if (!inputType.contains("textPostalAddress")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is a postal address, "
                                + "but the `inputType` does not include `textPostalAddress`");
            }
        }
    }

    /**
     * Checks if the given id string contains the given word (as a word boundary match,
     * considering underscores, camelCase, and other separators as boundaries).
     */
    private static boolean idContainsWord(@NonNull String id, @NonNull String word) {
        int index = id.indexOf(word);
        if (index == -1) {
            return false;
        }

        // Check word boundaries
        if (index > 0) {
            char before = id.charAt(index - 1);
            if (Character.isLetterOrDigit(before)) {
                return false;
            }
        }

        int end = index + word.length();
        if (end < id.length()) {
            char after = id.charAt(end);
            if (Character.isLetterOrDigit(after)) {
                return false;
            }
        }

        return true;
    }
}