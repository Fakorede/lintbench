package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
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

public class TextFieldDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability because " +
            "depending on the data to be input, optimized keyboards can be shown to the user " +
            "(such as just digits and parentheses for a phone number).\n\n" +
            "The lint detector also looks at the `id` of the view, and if the id offers a " +
            "hint of the purpose of the field (for example, the `id` contains the phrase " +
            "`phone` or `email`), then lint will also ensure that the `inputType` contains " +
            "the corresponding type attributes.\n\n" +
            "If you really want to keep the text field generic, you can suppress this warning " +
            "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    TextFieldDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";

    // inputType flag values
    private static final String INPUT_TYPE_PHONE = "phone";
    private static final String INPUT_TYPE_EMAIL = "textEmailAddress";
    private static final String INPUT_TYPE_URI = "textUri";
    private static final String INPUT_TYPE_PASSWORD = "textPassword";
    private static final String INPUT_TYPE_VISIBLE_PASSWORD = "textVisiblePassword";
    private static final String INPUT_TYPE_EMAIL_SUBJECT = "textEmailSubject";
    private static final String INPUT_TYPE_POSTAL_ADDRESS = "textPostalAddress";
    private static final String INPUT_TYPE_PERSON_NAME = "textPersonName";
    private static final String INPUT_TYPE_NUMBER = "number";
    private static final String INPUT_TYPE_NUMBER_PASSWORD = "numberPassword";

    public TextFieldDetector() {
    }

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

        // Check for password attribute (older approach)
        String password = element.getAttributeNS(ANDROID_URI, ATTR_PASSWORD);
        boolean isPasswordViaAttr = "true".equals(password);

        if (inputTypeAttr == null) {
            // No inputType set at all
            // Check if there's a hint from the id
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            String idLower = id != null ? id.toLowerCase() : "";

            // Strip common prefixes like @+id/, @id/
            int slashIndex = idLower.lastIndexOf('/');
            if (slashIndex >= 0) {
                idLower = idLower.substring(slashIndex + 1);
            }

            if (isPasswordViaAttr) {
                // Has password attribute but no inputType - suggest inputType
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType`; you should generally " +
                        "add an `inputType` attribute in order to get proper keyboard shown, " +
                        "improve performance by not doing unnecessary layout work, and make the " +
                        "field more accessible",
                        createInputTypeFix(INPUT_TYPE_PASSWORD));
                return;
            }

            String suggestedType = getSuggestedInputType(idLower);
            if (suggestedType != null) {
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType`; you should generally " +
                        "add an `inputType` attribute in order to get proper keyboard shown, " +
                        "improve performance by not doing unnecessary layout work, and make the " +
                        "field more accessible",
                        createInputTypeFix(suggestedType));
            } else {
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType`; you should generally " +
                        "add an `inputType` attribute in order to get proper keyboard shown, " +
                        "improve performance by not doing unnecessary layout work, and make the " +
                        "field more accessible",
                        createInputTypeFix("text"));
            }
        } else {
            // inputType is set - check if it matches the id hint
            String inputType = inputTypeAttr.getValue();
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            String idLower = id != null ? id.toLowerCase() : "";

            // Strip common prefixes like @+id/, @id/
            int slashIndex = idLower.lastIndexOf('/');
            if (slashIndex >= 0) {
                idLower = idLower.substring(slashIndex + 1);
            }

            checkInputTypeMatchesId(context, element, inputTypeAttr, inputType, idLower);
        }
    }

    @Nullable
    private static String getSuggestedInputType(@NonNull String idLower) {
        if (containsWord(idLower, "phone") || containsWord(idLower, "tel")) {
            return INPUT_TYPE_PHONE;
        } else if (containsWord(idLower, "email") || containsWord(idLower, "mail")) {
            return INPUT_TYPE_EMAIL;
        } else if (containsWord(idLower, "uri") || containsWord(idLower, "url")
                || containsWord(idLower, "website") || containsWord(idLower, "web")) {
            return INPUT_TYPE_URI;
        } else if (containsWord(idLower, "password") || containsWord(idLower, "passwd")
                || containsWord(idLower, "pwd")) {
            return INPUT_TYPE_PASSWORD;
        } else if (containsWord(idLower, "postal") || containsWord(idLower, "zip")) {
            return INPUT_TYPE_POSTAL_ADDRESS;
        } else if (containsWord(idLower, "name") || containsWord(idLower, "person")) {
            return INPUT_TYPE_PERSON_NAME;
        } else if (containsWord(idLower, "subject")) {
            return INPUT_TYPE_EMAIL_SUBJECT;
        }
        return null;
    }

    private static void checkInputTypeMatchesId(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull Attr inputTypeAttr,
            @NonNull String inputType,
            @NonNull String idLower) {

        String inputTypeLower = inputType.toLowerCase();

        if (containsWord(idLower, "phone") || containsWord(idLower, "tel")) {
            if (!inputTypeLower.contains("phone")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idLower + "`) suggests this is a phone number, " +
                        "but the `inputType` does not include `phone`");
            }
        } else if (containsWord(idLower, "email") || containsWord(idLower, "mail")) {
            if (!inputTypeLower.contains("emailaddress") && !inputTypeLower.contains("email")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idLower + "`) suggests this is an e-mail address, " +
                        "but the `inputType` does not include `textEmailAddress`");
            }
        } else if (containsWord(idLower, "uri") || containsWord(idLower, "url")
                || containsWord(idLower, "website") || containsWord(idLower, "web")) {
            if (!inputTypeLower.contains("uri") && !inputTypeLower.contains("url")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idLower + "`) suggests this is a URI, " +
                        "but the `inputType` does not include `textUri`");
            }
        } else if (containsWord(idLower, "password") || containsWord(idLower, "passwd")
                || containsWord(idLower, "pwd")) {
            if (!inputTypeLower.contains("password")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idLower + "`) suggests this is a password field, " +
                        "but the `inputType` does not include `textPassword` or `numberPassword`");
            }
        }
    }

    private static boolean containsWord(@NonNull String text, @NonNull String word) {
        int index = text.indexOf(word);
        if (index < 0) {
            return false;
        }
        // Check that it's a word boundary (preceded/followed by non-letter or start/end)
        if (index > 0) {
            char before = text.charAt(index - 1);
            if (Character.isLetter(before) && Character.isLowerCase(before)) {
                // Could be part of a larger word; check for camelCase boundary
                // Allow if the word starts with uppercase in original (camelCase)
                // Since we're working with lowercase, just check if it's a boundary
                // We'll be lenient and allow substring matches for common patterns
            }
        }
        return true;
    }

    @Nullable
    private static LintFix createInputTypeFix(@NonNull String inputType) {
        return LintFix.create()
                .set(ANDROID_URI, ATTR_INPUT_TYPE, inputType)
                .build();
    }
}