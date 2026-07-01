package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Locale;

public class TextFieldDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability " +
            "because depending on the data to be input, optimized keyboards can be shown " +
            "to the user (such as just digits and parentheses for a phone number).\n\n" +
            "The lint detector also looks at the `id` of the view, and if the id offers a " +
            "hint of the purpose of the field (for example, the `id` contains the phrase " +
            "`phone` or `email`), then lint will also ensure that the `inputType` contains " +
            "the corresponding type attributes.\n\n" +
            "If you really want to keep the text field generic, you can suppress this warning " +
            "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (!tag.equals(SdkConstants.EDIT_TEXT) && !tag.endsWith("." + SdkConstants.EDIT_TEXT)) {
            return;
        }

        Attr inputTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);

        if (inputTypeAttr == null) {
            String message = "This text field does not specify an `inputType`";
            context.report(ISSUE, element, context.getLocation(element), message);
            return;
        }

        String inputTypeValue = inputTypeAttr.getValue();
        if (inputTypeValue.startsWith("@")) {
            return;
        }

        if ("text".equals(inputTypeValue)) {
            return;
        }

        if (idAttr != null) {
            String id = idAttr.getValue();
            int slash = id.lastIndexOf('/');
            if (slash != -1) {
                id = id.substring(slash + 1);
            }
            String lowerId = id.toLowerCase(Locale.US);

            checkHint(context, inputTypeAttr, inputTypeValue, lowerId, "phone", "phone");
            checkHint(context, inputTypeAttr, inputTypeValue, lowerId, "email", "textEmailAddress");
            checkHint(context, inputTypeAttr, inputTypeValue, lowerId, "password", "textPassword");
            checkHint(context, inputTypeAttr, inputTypeValue, lowerId, "number", "number");
            checkHint(context, inputTypeAttr, inputTypeValue, lowerId, "date", "date");
            checkHint(context, inputTypeAttr, inputTypeValue, lowerId, "time", "time");
            checkHint(context, inputTypeAttr, inputTypeValue, lowerId, "url", "textUri");
            checkHint(context, inputTypeAttr, inputTypeValue, lowerId, "postal", "textPostalAddress");
            checkHint(context, inputTypeAttr, inputTypeValue, lowerId, "address", "textPostalAddress");
        }
    }

    private void checkHint(XmlContext context, Attr inputTypeAttr, String inputTypeValue, String lowerId, String hint, String expectedType) {
        if (containsWord(lowerId, hint)) {
            if (!hasInputType(inputTypeValue, expectedType)) {
                String message = String.format(
                        "The id suggests this is a %s field, but the inputType is missing `%s`",
                        hint, expectedType);
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr), message);
            }
        }
    }

    private static boolean hasInputType(String inputTypeValue, String expectedType) {
        String[] types = inputTypeValue.split("\\|");
        for (String type : types) {
            if (type.trim().equals(expectedType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsWord(String name, String word) {
        return containsWord(name, word, false, false);
    }

    public static boolean containsWord(String name, String word, boolean allowPrefix, boolean allowSuffix) {
        if (name == null || word == null) {
            return false;
        }
        String lowerName = name.toLowerCase(Locale.US);
        String lowerWord = word.toLowerCase(Locale.US);
        int index = 0;
        int nameLen = lowerName.length();
        int wordLen = lowerWord.length();
        while (index <= nameLen - wordLen) {
            int found = lowerName.indexOf(lowerWord, index);
            if (found == -1) {
                return false;
            }
            boolean prefixOk = allowPrefix || found == 0 || !Character.isLetterOrDigit(lowerName.charAt(found - 1));
            int end = found + wordLen;
            boolean suffixOk = allowSuffix || end == nameLen || !Character.isLetterOrDigit(lowerName.charAt(end));
            if (prefixOk && suffixOk) {
                return true;
            }
            index = found + 1;
        }
        return false;
    }
}