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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.EDIT_TEXT;

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
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        if (inputTypeAttr == null) {
            // Check if there's a hint attribute that might indicate the purpose
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String id = idAttr != null ? idAttr.getValue().toLowerCase() : "";

            // Strip @+id/ or @id/ prefix
            if (id.startsWith("@+id/")) {
                id = id.substring(5);
            } else if (id.startsWith("@id/")) {
                id = id.substring(4);
            }

            String message = "This text field does not specify an `inputType` or a `hint`";

            Attr hintAttr = element.getAttributeNodeNS(ANDROID_URI, "hint");
            if (hintAttr == null) {
                context.report(ISSUE, element, context.getLocation(element), message);
            } else {
                // Has a hint but no inputType - still warn
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType`");
            }
            return;
        }

        // Has inputType - check if the id suggests a specific type that should be set
        String inputType = inputTypeAttr.getValue().toLowerCase();

        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue().toLowerCase();
        // Strip @+id/ or @id/ prefix
        if (id.startsWith("@+id/")) {
            id = id.substring(5);
        } else if (id.startsWith("@id/")) {
            id = id.substring(4);
        }

        if (id.isEmpty()) {
            return;
        }

        // Check for phone-related fields
        if (containsWord(id, "phone") || containsWord(id, "tel")) {
            if (!inputType.contains("phone")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a phone "
                                + "number, but the `inputType` does not include `phone`");
            }
        }

        // Check for email-related fields
        if (containsWord(id, "email") || containsWord(id, "mail")) {
            if (!inputType.contains("textEmailAddress")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is an email "
                                + "address, but the `inputType` does not include `textEmailAddress`");
            }
        }

        // Check for password-related fields
        if (containsWord(id, "password") || containsWord(id, "passwd") || containsWord(id, "pwd")) {
            if (!inputType.contains("textPassword") && !inputType.contains("numberPassword")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a password "
                                + "field, but the `inputType` does not include `textPassword`");
            }
        }

        // Check for URI/URL-related fields
        if (containsWord(id, "url") || containsWord(id, "uri") || containsWord(id, "website")
                || containsWord(id, "link")) {
            if (!inputType.contains("textUri")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a URI, "
                                + "but the `inputType` does not include `textUri`");
            }
        }

        // Check for postal code / zip fields
        if (containsWord(id, "postal") || containsWord(id, "zip")) {
            if (!inputType.contains("number") && !inputType.contains("text")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a postal "
                                + "code, but the `inputType` does not include a number or text type");
            }
        }

        // Check for person name fields
        if (containsWord(id, "name") && !containsWord(id, "username") && !containsWord(id, "user_name")) {
            if (!inputType.contains("textPersonName") && !inputType.contains("textCapWords")
                    && !inputType.contains("text")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a person "
                                + "name, but the `inputType` does not include `textPersonName`");
            }
        }
    }

    /**
     * Checks if the given string contains the given word, treating underscores, dashes,
     * and camelCase boundaries as word separators.
     */
    private static boolean containsWord(@NonNull String id, @NonNull String word) {
        // Simple substring check (case already lowercased)
        if (!id.contains(word)) {
            return false;
        }

        // Make sure it's a whole word (bounded by start, end, underscore, dash, or camel boundary)
        int index = id.indexOf(word);
        while (index != -1) {
            boolean startOk = (index == 0)
                    || !Character.isLetterOrDigit(id.charAt(index - 1))
                    || Character.isUpperCase(id.charAt(index));
            boolean endOk = (index + word.length() == id.length())
                    || !Character.isLetterOrDigit(id.charAt(index + word.length()))
                    || Character.isUpperCase(id.charAt(index + word.length()));
            if (startOk && endOk) {
                return true;
            }
            index = id.indexOf(word, index + 1);
        }
        return false;
    }
}