package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;

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

    private static final String INPUT_TYPE_PHONE = "phone";
    private static final String INPUT_TYPE_EMAIL = "textEmailAddress";
    private static final String INPUT_TYPE_URI = "textUri";
    private static final String INPUT_TYPE_PASSWORD = "textPassword";
    private static final String INPUT_TYPE_POSTAL_ADDRESS = "textPostalAddress";
    private static final String INPUT_TYPE_PERSON_NAME = "textPersonName";
    private static final String INPUT_TYPE_EMAIL_SUBJECT = "textEmailSubject";

    public TextFieldDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
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
        String password = element.getAttributeNS(ANDROID_URI, "password");
        boolean isPasswordViaAttr = "true".equals(password);

        // Get the id and normalize it
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        String idLower = id != null ? id.toLowerCase() : "";
        int slashIndex = idLower.lastIndexOf('/');
        if (slashIndex >= 0) {
            idLower = idLower.substring(slashIndex + 1);
        }

        if (inputTypeAttr == null) {
            if (isPasswordViaAttr) {
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType`; you should generally " +
                        "add an `inputType` attribute in order to get proper keyboard shown, " +
                        "improve performance by not doing unnecessary layout work, and make the " +
                        "field more accessible");
                return;
            }

            String suggestedType = getSuggestedInputType(idLower);
            if (suggestedType != null) {
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType`; you should generally " +
                        "add an `inputType` attribute in order to get proper keyboard shown, " +
                        "improve performance by not doing unnecessary layout work, and make the " +
                        "field more accessible");
            } else {
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType`; you should generally " +
                        "add an `inputType` attribute in order to get proper keyboard shown, " +
                        "improve performance by not doing unnecessary layout work, and make the " +
                        "field more accessible");
            }
        } else {
            // inputType is set - check if it matches the id hint
            String inputType = inputTypeAttr.getValue();
            checkInputTypeMatchesId(context, element, inputTypeAttr, inputType, idLower);
        }
    }

    @Nullable
    private static String getSuggestedInputType(@NonNull String idLower) {
        if (containsWord(idLower, "phone", true, true) || containsWord(idLower, "tel", true, true)) {
            return INPUT_TYPE_PHONE;
        } else if (containsWord(idLower, "email", true, true) || containsWord(idLower, "mail", true, true)) {
            return INPUT_TYPE_EMAIL;
        } else if (containsWord(idLower, "uri", true, true) || containsWord(idLower, "url", true, true)
                || containsWord(idLower, "website", true, true) || containsWord(idLower, "web", true, true)) {
            return INPUT_TYPE_URI;
        } else if (containsWord(idLower, "password", true, true) || containsWord(idLower, "passwd", true, true)
                || containsWord(idLower, "pwd", true, true)) {
            return INPUT_TYPE_PASSWORD;
        } else if (containsWord(idLower, "postal", true, true) || containsWord(idLower, "zip", true, true)) {
            return INPUT_TYPE_POSTAL_ADDRESS;
        } else if (containsWord(idLower, "name", true, true) || containsWord(idLower, "person", true, true)) {
            return INPUT_TYPE_PERSON_NAME;
        } else if (containsWord(idLower, "subject", true, true)) {
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

        if (containsWord(idLower, "phone", true, true) || containsWord(idLower, "tel", true, true)) {
            if (!inputTypeLower.contains("phone")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idLower + "`) suggests this is a phone number, " +
                        "but the `inputType` does not include `phone`");
            }
        } else if (containsWord(idLower, "email", true, true) || containsWord(idLower, "mail", true, true)) {
            if (!inputTypeLower.contains("emailaddress") && !inputTypeLower.contains("email")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idLower + "`) suggests this is an e-mail address, " +
                        "but the `inputType` does not include `textEmailAddress`");
            }
        } else if (containsWord(idLower, "uri", true, true) || containsWord(idLower, "url", true, true)
                || containsWord(idLower, "website", true, true) || containsWord(idLower, "web", true, true)) {
            if (!inputTypeLower.contains("uri") && !inputTypeLower.contains("url")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idLower + "`) suggests this is a URI, " +
                        "but the `inputType` does not include `textUri`");
            }
        } else if (containsWord(idLower, "password", true, true) || containsWord(idLower, "passwd", true, true)
                || containsWord(idLower, "pwd", true, true)) {
            if (!inputTypeLower.contains("password")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idLower + "`) suggests this is a password field, " +
                        "but the `inputType` does not include `textPassword` or `numberPassword`");
            }
        }
    }

    /**
     * Returns true if the given name contains the given word. The allowPrefix and allowSuffix
     * parameters control whether the word can be preceded/followed by other word characters.
     */
    public static boolean containsWord(@NonNull String name, @NonNull String word,
            boolean allowPrefix, boolean allowSuffix) {
        int index = 0;
        int nameLen = name.length();
        int wordLen = word.length();

        while (true) {
            index = name.indexOf(word, index);
            if (index < 0) {
                return false;
            }

            // Check prefix boundary
            if (!allowPrefix && index > 0) {
                char before = name.charAt(index - 1);
                if (Character.isLetterOrDigit(before)) {
                    index++;
                    continue;
                }
            }

            // Check suffix boundary
            int end = index + wordLen;
            if (!allowSuffix && end < nameLen) {
                char after = name.charAt(end);
                if (Character.isLetterOrDigit(after)) {
                    index++;
                    continue;
                }
            }

            return true;
        }
    }

    /**
     * Returns true if the given name contains the given word (as a substring).
     */
    public static boolean containsWord(@NonNull String name, @NonNull String word) {
        return containsWord(name, word, true, true);
    }
}