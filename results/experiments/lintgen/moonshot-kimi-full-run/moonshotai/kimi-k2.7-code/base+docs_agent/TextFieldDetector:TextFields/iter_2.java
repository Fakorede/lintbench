package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class TextFieldDetector extends Detector implements XmlScanner {

    private static final String[] TEXT_FIELDS = new String[] {
            "EditText",
            "AutoCompleteTextView",
            "MultiAutoCompleteTextView",
            "com.google.android.material.textfield.TextInputEditText",
            "android.support.design.widget.TextInputEditText"
    };

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType",
            "Providing an `inputType` attribute on a text field improves usability because "
                    + "depending on the data to be input, optimized keyboards can be shown to the "
                    + "user (such as just digits and parentheses for a phone number).\n\n"
                    + "The lint detector also looks at the `id` of the view, and if the id offers a "
                    + "hint of the purpose of the field (for example, the id contains the phrase "
                    + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                    + "the corresponding type attributes.\n\n"
                    + "If you really want to keep the text field generic, you can suppress this warning "
                    + "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TEXT_FIELDS);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        String hint = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_HINT);

        if (inputType.isEmpty() && hint.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an inputType or the hint is missing");
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        if (containsWord(id, "phone", true, false) && !inputType.contains("phone")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id `" + id + "` suggests this is a phone number field, so use an inputType of `phone`");
        }

        if (containsWord(id, "email", true, true) && !inputType.contains("email")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id `" + id + "` suggests this is an email field, so use an inputType of `textEmailAddress`");
        }

        if (containsWord(id, "password", true, false) && !inputType.contains("password")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id `" + id + "` suggests this is a password field, so use an inputType of `textPassword`");
        }

        if (containsWord(id, "url", true, true) && !inputType.contains("textUri")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id `" + id + "` suggests this is a URL field, so use an inputType of `textUri`");
        }
    }

    public static boolean containsWord(String name, String word) {
        return containsWord(name, word, false, false);
    }

    public static boolean containsWord(String name, String word, boolean allowPrefix, boolean allowSuffix) {
        if (name == null || word == null) {
            return false;
        }

        int index = name.indexOf(word);
        while (index != -1) {
            int before = index - 1;
            int after = index + word.length();

            boolean prefixOk = before < 0 || !Character.isLetterOrDigit(name.charAt(before));
            boolean suffixOk = after >= name.length() || !Character.isLetterOrDigit(name.charAt(after));

            if ((allowPrefix || prefixOk) && (allowSuffix || suffixOk)) {
                return true;
            }

            index = name.indexOf(word, index + 1);
        }

        return false;
    }
}