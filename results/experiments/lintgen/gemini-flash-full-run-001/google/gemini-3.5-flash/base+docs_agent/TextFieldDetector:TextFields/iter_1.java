package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType or useful inputType",
            "Providing an `inputType` attribute on a text field improves usability "
                    + "because depending on the data to be input, optimized keyboards can be shown "
                    + "to the user (such as just digits and parentheses for a phone number).\n\n"
                    + "The lint detector also looks at the `id` of the view, and if the id offers a "
                    + "hint of the purpose of the field (for example, the `id` contains the phrase "
                    + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                    + "the corresponding type attributes.\n\n"
                    + "If you really want to keep the text field generic, you can suppress this warning "
                    + "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    TextFieldDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.EDIT_TEXT,
                "android.widget.EditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (inputType == null || inputType.isEmpty()) {
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "numeric")
                    || element.hasAttributeNS(SdkConstants.ANDROID_URI, "phoneNumber")
                    || element.hasAttributeNS(SdkConstants.ANDROID_URI, "password")
                    || element.hasAttributeNS(SdkConstants.ANDROID_URI, "inputMethod")) {
                return;
            }

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `inputType` attribute"
            );
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = id.substring(id.lastIndexOf('/') + 1);

            Attr attributeNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
            if (attributeNode == null) {
                attributeNode = element.getAttributeNode(SdkConstants.ATTR_INPUT_TYPE);
            }
            if (attributeNode == null) {
                attributeNode = element.getAttributeNode("android:" + SdkConstants.ATTR_INPUT_TYPE);
            }
            Location location = attributeNode != null ? context.getValueLocation(attributeNode) : context.getNameLocation(element);

            if (containsWord(idName, "email", true, true)) {
                if (!inputType.contains("textEmailAddress") && !inputType.contains("textWebEmailAddress")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "This text field is named like an email field, but does not use the corresponding `inputType` (such as `textEmailAddress`)"
                    );
                }
            } else if (containsWord(idName, "phone", true, true)) {
                if (!inputType.contains("phone")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "This text field is named like a phone number field, but does not use the corresponding `inputType` (`phone`)"
                    );
                }
            } else if (containsWord(idName, "password", true, true)) {
                if (!inputType.contains("Password") && !inputType.contains("password")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "This text field is named like a password field, but does not use the corresponding `inputType` (such as `textPassword`)"
                    );
                }
            } else if (containsWord(idName, "url", true, true) || containsWord(idName, "uri", true, true) || containsWord(idName, "website", true, true)) {
                if (!inputType.contains("textUri")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "This text field is named like a URL field, but does not use the corresponding `inputType` (`textUri`)"
                    );
                }
            } else if (containsWord(idName, "search", true, true)) {
                if (!inputType.contains("textSearch")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "This text field is named like a search field, but does not use the corresponding `inputType` (`textSearch`)"
                    );
                }
            } else if (containsWord(idName, "date", true, true)) {
                if (!inputType.contains("date") && !inputType.contains("time") && !inputType.contains("datetime")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "This text field is named like a date field, but does not use the corresponding `inputType` (`date` or `datetime`)"
                    );
                }
            } else if (containsWord(idName, "number", true, true) || containsWord(idName, "numeric", true, true)) {
                if (!inputType.contains("number") && !inputType.contains("phone") && !inputType.contains("date")
                        && !inputType.contains("time") && !inputType.contains("decimal") && !inputType.contains("signed")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "This text field is named like a numeric field, but does not use the corresponding `inputType` (such as `number`)"
                    );
                }
            }
        }
    }

    public static boolean containsWord(String name, String word) {
        return containsWord(name, word, true, true);
    }

    public static boolean containsWord(String name, String word, boolean allowPrefix, boolean allowSuffix) {
        if (name == null || word == null) {
            return false;
        }
        int index = 0;
        int wordLength = word.length();
        while (true) {
            index = name.indexOf(word, index);
            if (index == -1) {
                String lowerName = name.toLowerCase(Locale.US);
                String lowerWord = word.toLowerCase(Locale.US);
                index = 0;
                while (true) {
                    index = lowerName.indexOf(lowerWord, index);
                    if (index == -1) {
                        return false;
                    }
                    if (isWordBoundary(name, index, wordLength, allowPrefix, allowSuffix)) {
                        return true;
                    }
                    index += wordLength;
                }
            }
            if (isWordBoundary(name, index, wordLength, allowPrefix, allowSuffix)) {
                return true;
            }
            index += wordLength;
        }
    }

    private static boolean isWordBoundary(
            String name,
            int index,
            int wordLength,
            boolean allowPrefix,
            boolean allowSuffix) {
        if (index > 0) {
            char prev = name.charAt(index - 1);
            if (Character.isLetter(prev)) {
                if (!allowPrefix) {
                    char first = name.charAt(index);
                    if (!Character.isLowerCase(prev) || !Character.isUpperCase(first)) {
                        return false;
                    }
                }
            } else if (Character.isDigit(prev)) {
                if (!allowPrefix) {
                    return false;
                }
            }
        }
        if (index + wordLength < name.length()) {
            char next = name.charAt(index + wordLength);
            if (Character.isLetter(next)) {
                if (!allowSuffix) {
                    char last = name.charAt(index + wordLength - 1);
                    if (!Character.isLowerCase(last) || !Character.isUpperCase(next)) {
                        return false;
                    }
                }
            } else if (Character.isDigit(next)) {
                if (!allowSuffix) {
                    return false;
                }
            }
        }
        return true;
    }
}