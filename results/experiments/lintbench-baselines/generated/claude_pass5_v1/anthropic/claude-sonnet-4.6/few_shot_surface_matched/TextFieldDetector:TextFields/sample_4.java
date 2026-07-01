package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.ATTR_PASSWORD;
import static com.android.SdkConstants.ATTR_PHONE_NUMBER;

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

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";

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
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        // Check for legacy password/phoneNumber attributes
        String phoneNumber = element.getAttributeNS(ANDROID_URI, ATTR_PHONE_NUMBER);
        String password = element.getAttributeNS(ANDROID_URI, ATTR_PASSWORD);

        if (inputTypeAttr == null) {
            // No inputType attribute set
            // Check if legacy attributes are set that imply a type
            if ("true".equals(phoneNumber) || "true".equals(password)) {
                // Legacy attributes set, no need to warn
                return;
            }

            // Get the id to check for hints about the field's purpose
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            if (id != null) {
                id = id.toLowerCase();
                // Strip common prefixes like @+id/ or @id/
                int slashIndex = id.lastIndexOf('/');
                if (slashIndex != -1) {
                    id = id.substring(slashIndex + 1);
                }
            }

            String message = "Text field does not specify an `inputType`";
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);

            if (id != null && !id.isEmpty()) {
                if (containsWord(id, "phone") || containsWord(id, "tel")) {
                    message = "Text field does not specify an `inputType` (expected `phone`)";
                } else if (containsWord(id, "email") || containsWord(id, "mail")) {
                    message = "Text field does not specify an `inputType` (expected `textEmailAddress`)";
                } else if (containsWord(id, "password") || containsWord(id, "passwd")
                        || containsWord(id, "pwd")) {
                    message = "Text field does not specify an `inputType` (expected `textPassword`)";
                } else if (containsWord(id, "uri") || containsWord(id, "url")
                        || containsWord(id, "link") || containsWord(id, "website")) {
                    message = "Text field does not specify an `inputType` (expected `textUri`)";
                } else if (containsWord(id, "postal") || containsWord(id, "zip")) {
                    message = "Text field does not specify an `inputType` (expected `textPostalAddress`)";
                }
            }

            if (idAttr != null) {
                context.report(ISSUE, element, context.getLocation(idAttr), message);
            } else {
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        } else {
            // inputType is set; check if it's consistent with the id hint
            String inputType = inputTypeAttr.getValue().toLowerCase();
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            if (id != null) {
                id = id.toLowerCase();
                int slashIndex = id.lastIndexOf('/');
                if (slashIndex != -1) {
                    id = id.substring(slashIndex + 1);
                }
            }

            if (id == null || id.isEmpty()) {
                return;
            }

            if ((containsWord(id, "phone") || containsWord(id, "tel"))
                    && !inputType.contains("phone")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests this is a phone number, but `inputType` does not include `phone`");
            } else if ((containsWord(id, "email") || containsWord(id, "mail"))
                    && !inputType.contains("emailaddress") && !inputType.contains("email")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests this is an e-mail address, but `inputType` does not include `textEmailAddress`");
            } else if ((containsWord(id, "password") || containsWord(id, "passwd")
                    || containsWord(id, "pwd"))
                    && !inputType.contains("password")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests this is a password field, but `inputType` does not include `textPassword`");
            } else if ((containsWord(id, "uri") || containsWord(id, "url")
                    || containsWord(id, "link") || containsWord(id, "website"))
                    && !inputType.contains("uri")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests this is a URI, but `inputType` does not include `textUri`");
            } else if ((containsWord(id, "postal") || containsWord(id, "zip"))
                    && !inputType.contains("postaladdress")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests this is a postal address, but `inputType` does not include `textPostalAddress`");
            }
        }
    }

    /**
     * Returns true if the given id string contains the given word, either as a standalone word,
     * as a camelCase component, or separated by underscores/hyphens.
     */
    private static boolean containsWord(@NonNull String id, @NonNull String word) {
        // Direct contains check (handles camelCase and simple cases)
        int index = id.indexOf(word);
        while (index != -1) {
            // Check that it's a word boundary
            boolean startOk = index == 0
                    || !Character.isLetterOrDigit(id.charAt(index - 1))
                    || Character.isUpperCase(word.charAt(0));
            boolean endOk = (index + word.length()) >= id.length()
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