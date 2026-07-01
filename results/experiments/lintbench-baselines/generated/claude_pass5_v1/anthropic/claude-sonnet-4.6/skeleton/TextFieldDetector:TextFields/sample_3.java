package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.ATTR_PASSWORD;
import static com.android.SdkConstants.ATTR_PHONE_NUMBER;
import static com.android.SdkConstants.CLASS_EDIT_TEXT;
import static com.android.SdkConstants.EDIT_TEXT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if inputType attribute is present
        if (element.hasAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE)) {
            // inputType is set; check if it matches hints from the id
            String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
            checkInputTypeMatchesId(context, element, inputType);
            return;
        }

        // Also check for the older password/phoneNumber attributes
        if (element.hasAttributeNS(ANDROID_URI, ATTR_PASSWORD)
                || element.hasAttributeNS(ANDROID_URI, ATTR_PHONE_NUMBER)) {
            // These are older equivalents; don't flag
            return;
        }

        // No inputType set — report the issue
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        String message = "This text field does not specify an `inputType` or a `hint`";

        // Suggest a fix based on the id
        String suggestedInputType = suggestInputType(id);
        LintFix fix = null;
        if (suggestedInputType != null) {
            fix = LintFix.create()
                    .set(ANDROID_URI, ATTR_INPUT_TYPE, suggestedInputType)
                    .build();
        } else {
            fix = LintFix.create()
                    .set(ANDROID_URI, ATTR_INPUT_TYPE, "text")
                    .build();
        }

        Attr node = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (node != null) {
            context.report(ISSUE, element, context.getLocation(node), message, fix);
        } else {
            context.report(ISSUE, element, context.getLocation(element), message, fix);
        }
    }

    private void checkInputTypeMatchesId(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String inputType) {
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String idLower = id.toLowerCase();

        // Check for phone-related id
        if (containsWord(idLower, "phone")) {
            if (!inputType.contains("phone")) {
                String message =
                        "The view's `id` (`"
                                + id
                                + "`) suggests this is a phone number, but the `inputType` does not "
                                + "include `phone` or `dialableChars`";
                Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
                if (inputTypeAttr != null) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(inputTypeAttr),
                            message,
                            LintFix.create()
                                    .set(ANDROID_URI, ATTR_INPUT_TYPE, "phone")
                                    .build());
                }
            }
        }

        // Check for email-related id
        if (containsWord(idLower, "email")) {
            if (!inputType.contains("textEmailAddress")) {
                String message =
                        "The view's `id` (`"
                                + id
                                + "`) suggests this is an e-mail address, but the `inputType` does not "
                                + "include `textEmailAddress`";
                Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
                if (inputTypeAttr != null) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(inputTypeAttr),
                            message,
                            LintFix.create()
                                    .set(ANDROID_URI, ATTR_INPUT_TYPE, "textEmailAddress")
                                    .build());
                }
            }
        }

        // Check for password-related id
        if (containsWord(idLower, "password") || containsWord(idLower, "passwd")) {
            if (!inputType.contains("textPassword")
                    && !inputType.contains("textVisiblePassword")
                    && !inputType.contains("numberPassword")) {
                String message =
                        "The view's `id` (`"
                                + id
                                + "`) suggests this is a password field, but the `inputType` does not "
                                + "include `textPassword`";
                Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
                if (inputTypeAttr != null) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(inputTypeAttr),
                            message,
                            LintFix.create()
                                    .set(ANDROID_URI, ATTR_INPUT_TYPE, "textPassword")
                                    .build());
                }
            }
        }

        // Check for URI/URL-related id
        if (containsWord(idLower, "uri") || containsWord(idLower, "url")) {
            if (!inputType.contains("textUri")) {
                String message =
                        "The view's `id` (`"
                                + id
                                + "`) suggests this is a URI, but the `inputType` does not "
                                + "include `textUri`";
                Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
                if (inputTypeAttr != null) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(inputTypeAttr),
                            message,
                            LintFix.create()
                                    .set(ANDROID_URI, ATTR_INPUT_TYPE, "textUri")
                                    .build());
                }
            }
        }
    }

    private static String suggestInputType(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String idLower = id.toLowerCase();

        if (containsWord(idLower, "phone")) {
            return "phone";
        }
        if (containsWord(idLower, "email")) {
            return "textEmailAddress";
        }
        if (containsWord(idLower, "password") || containsWord(idLower, "passwd")) {
            return "textPassword";
        }
        if (containsWord(idLower, "uri") || containsWord(idLower, "url")) {
            return "textUri";
        }
        if (containsWord(idLower, "postal") || containsWord(idLower, "zip")) {
            return "number";
        }

        return null;
    }

    /**
     * Returns true if the given string contains the given word (where word boundaries are
     * underscores, camelCase transitions, or start/end of string).
     */
    private static boolean containsWord(@NonNull String s, @NonNull String word) {
        int index = s.indexOf(word);
        if (index == -1) {
            return false;
        }
        // Check that the match is at a word boundary
        // Word boundaries: start/end of string, underscore, or camelCase boundary
        int end = index + word.length();
        boolean startBoundary = index == 0
                || s.charAt(index - 1) == '_'
                || s.charAt(index - 1) == '/'
                || Character.isUpperCase(s.charAt(index));
        boolean endBoundary = end == s.length()
                || s.charAt(end) == '_'
                || s.charAt(end) == '/'
                || Character.isUpperCase(s.charAt(end));
        if (startBoundary && endBoundary) {
            return true;
        }
        // Try to find another occurrence
        if (end < s.length()) {
            return containsWordFrom(s, word, end);
        }
        return false;
    }

    private static boolean containsWordFrom(@NonNull String s, @NonNull String word, int from) {
        int index = s.indexOf(word, from);
        if (index == -1) {
            return false;
        }
        int end = index + word.length();
        boolean startBoundary = index == 0
                || s.charAt(index - 1) == '_'
                || s.charAt(index - 1) == '/'
                || Character.isUpperCase(s.charAt(index));
        boolean endBoundary = end == s.length()
                || s.charAt(end) == '_'
                || s.charAt(end) == '/'
                || Character.isUpperCase(s.charAt(end));
        if (startBoundary && endBoundary) {
            return true;
        }
        if (end < s.length()) {
            return containsWordFrom(s, word, end);
        }
        return false;
    }
}