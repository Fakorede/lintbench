package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.ATTR_ID;

public class TextFieldDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType` attribute",
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
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
            "EditText",
            "AutoCompleteTextView",
            "MultiAutoCompleteTextView",
            "android.support.design.widget.TextInputEditText",
            "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);

        if (inputTypeAttr == null) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "Missing `inputType` attribute");
            return;
        }

        String inputType = inputTypeAttr.getValue();
        if ("text".equals(inputType)) {
            return;
        }

        if (idAttr != null) {
            String id = idAttr.getValue();
            String idName = id.substring(id.lastIndexOf('/') + 1);

            if (containsWord(idName, "phone", true, true) || containsWord(idName, "tel", true, true)) {
                if (!inputType.contains("phone")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is a phone number, but does not specify `inputType=\"phone\"`");
                }
            } else if (containsWord(idName, "email", true, true)) {
                if (!inputType.contains("textEmailAddress")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is an email address, but does not specify `inputType=\"textEmailAddress\"`");
                }
            } else if (containsWord(idName, "password", true, true)) {
                if (!inputType.contains("Password") && !inputType.contains("password")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is a password, but does not specify `inputType=\"textPassword\"` or similar");
                }
            } else if (containsWord(idName, "url", true, true) || containsWord(idName, "uri", true, true) || containsWord(idName, "website", true, true)) {
                if (!inputType.contains("textUri")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is a URL, but does not specify `inputType=\"textUri\"`");
                }
            } else if (containsWord(idName, "postal", true, true) || containsWord(idName, "zip", true, true)) {
                if (!inputType.contains("postalAddress") && !inputType.contains("number")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is a postal code, but does not specify `inputType=\"postalAddress\"` or `inputType=\"number\"`");
                }
            } else if (containsWord(idName, "numeric", true, true) || containsWord(idName, "number", true, true)) {
                if (!inputType.contains("number") && !inputType.contains("phone") && !inputType.contains("date")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is numeric, but does not specify `inputType=\"number\"` or similar");
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
        String nameLower = name.toLowerCase(Locale.US);
        String wordLower = word.toLowerCase(Locale.US);
        int index = nameLower.indexOf(wordLower);
        if (index == -1) {
            return false;
        }
        int length = name.length();
        int wordLength = word.length();
        while (index != -1) {
            boolean prefixOk = allowPrefix || index == 0 || !Character.isLetterOrDigit(name.charAt(index - 1));
            if (!prefixOk && index > 0) {
                char prev = name.charAt(index - 1);
                char curr = name.charAt(index);
                if (Character.isLowerCase(prev) && Character.isUpperCase(curr)) {
                    prefixOk = true;
                }
            }
            boolean suffixOk = allowSuffix || index + wordLength == length || !Character.isLetterOrDigit(name.charAt(index + wordLength));
            if (!suffixOk && index + wordLength < length) {
                char prev = name.charAt(index + wordLength - 1);
                char next = name.charAt(index + wordLength);
                if (Character.isLowerCase(prev) && Character.isUpperCase(next)) {
                    suffixOk = true;
                }
            }
            if (prefixOk && suffixOk) {
                return true;
            }
            index = nameLower.indexOf(wordLower, index + 1);
        }
        return false;
    }
}