package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType",
            "Providing an `inputType` attribute on a text field improves usability " +
            "because depending on the data to be input, optimized keyboards can be shown " +
            "to the user (such as just digits and parentheses for a phone number).\n" +
            "\n" +
            "The lint detector also looks at the `id` of the view, and if the id offers a " +
            "hint of the purpose of the field (for example, the `id` contains the phrase " +
            "`phone` or `email`), then lint will also ensure that the `inputType` contains " +
            "the corresponding type attributes.\n" +
            "\n" +
            "If you really want to keep the text field generic, you can suppress this warning " +
            "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    TextFieldDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW,
                "android.support.design.widget.TextInputEditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputType == null || inputType.isEmpty()) {
            if (element.hasAttributeNS(ANDROID_URI, "inputMethod")
                    || element.hasAttributeNS(ANDROID_URI, "numeric")
                    || element.hasAttributeNS(ANDROID_URI, "password")
                    || element.hasAttributeNS(ANDROID_URI, "phoneNumber")
                    || element.hasAttributeNS(ANDROID_URI, "digits")) {
                return;
            }
            String editable = element.getAttributeNS(ANDROID_URI, "editable");
            if ("false".equals(editable)) {
                return;
            }
            context.report(ISSUE, context.getNameLocation(element),
                    "Missing `inputType` attribute");
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = id.substring(id.indexOf('/') + 1);
            Attr attributeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
            Location location = attributeNode != null ? context.getValueLocation(attributeNode) : context.getNameLocation(element);

            if (containsWord(idName, "phone", true, true) || containsWord(idName, "tel", false, true)) {
                if (!inputType.contains("phone")) {
                    context.report(ISSUE, location,
                            "This text field is named like a phone number, but does not specify `inputType=\"phone\"`");
                }
            } else if (containsWord(idName, "email", true, true)) {
                if (!inputType.contains("textEmailAddress") && !inputType.contains("textWebEmailAddress")) {
                    context.report(ISSUE, location,
                            "This text field is named like an email address, but does not specify `inputType=\"textEmailAddress\"`");
                }
            } else if (containsWord(idName, "password", true, true)) {
                if (!inputType.contains("textPassword") && !inputType.contains("numberPassword")
                        && !inputType.contains("textVisiblePassword") && !inputType.contains("textWebPassword")) {
                    context.report(ISSUE, location,
                            "This text field is named like a password, but does not specify an inputType password type");
                }
            } else if (containsWord(idName, "pin", true, true)) {
                if (!inputType.contains("numberPassword") && !inputType.contains("textPassword")) {
                    context.report(ISSUE, location,
                            "This text field is named like a PIN, but does not specify `inputType=\"numberPassword\"`");
                }
            } else if (containsWord(idName, "postal", true, true) || containsWord(idName, "zip", true, true)) {
                if (!inputType.contains("textPostalAddress")) {
                    context.report(ISSUE, location,
                            "This text field is named like a postal address, but does not specify `inputType=\"textPostalAddress\"`");
                }
            } else if (containsWord(idName, "number", true, true) || containsWord(idName, "amount", true, true) || containsWord(idName, "quantity", true, true)) {
                if (!inputType.contains("number") && !inputType.contains("phone")) {
                    context.report(ISSUE, location,
                            "This text field is named like a number, but does not specify `inputType=\"number\"`");
                }
            } else if (containsWord(idName, "url", true, true) || containsWord(idName, "uri", true, true) || containsWord(idName, "website", true, true)) {
                if (!inputType.contains("textUri")) {
                    context.report(ISSUE, location,
                            "This text field is named like a URL, but does not specify `inputType=\"textUri\"`");
                }
            }
        }
    }

    public static boolean containsWord(
            @NonNull String name,
            @NonNull String word) {
        return containsWord(name, word, true, true);
    }

    public static boolean containsWord(
            @NonNull String name,
            @NonNull String word,
            boolean allowPrefix,
            boolean allowSuffix) {
        String lowerName = name.toLowerCase(Locale.US);
        String lowerWord = word.toLowerCase(Locale.US);
        int index = lowerName.indexOf(lowerWord);
        if (index == -1) {
            return false;
        }
        int length = name.length();
        int wordLength = word.length();
        while (index != -1) {
            boolean validStart = false;
            if (index == 0) {
                validStart = true;
            } else if (allowPrefix) {
                validStart = true;
            } else {
                char prev = name.charAt(index - 1);
                if (prev == '_' || prev == '-' || Character.isUpperCase(prev) || Character.isUpperCase(name.charAt(index))) {
                    validStart = true;
                }
            }

            boolean validEnd = false;
            if (index + wordLength == length) {
                validEnd = true;
            } else if (allowSuffix) {
                validEnd = true;
            } else {
                char next = name.charAt(index + wordLength);
                if (next == '_' || next == '-' || Character.isUpperCase(next)) {
                    validEnd = true;
                }
            }

            if (validStart && validEnd) {
                return true;
            }

            index = lowerName.indexOf(lowerWord, index + 1);
        }

        return false;
    }
}