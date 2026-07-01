package com.android.tools.lint.checks;

import android.text.InputType;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.w3c.dom.Element;

public class TextFieldDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability because "
                            + "depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number).",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String EXTRACT_EDIT_TEXT = "ExtractEditText";

    private static final Map<String, Integer> INPUT_TYPE_MAP = new HashMap<>();

    static {
        INPUT_TYPE_MAP.put("none", InputType.TYPE_NULL);
        INPUT_TYPE_MAP.put("text", InputType.TYPE_CLASS_TEXT);
        INPUT_TYPE_MAP.put(
                "textCapCharacters",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        INPUT_TYPE_MAP.put(
                "textCapWords", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        INPUT_TYPE_MAP.put(
                "textCapSentences",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        INPUT_TYPE_MAP.put(
                "textAutoCorrect",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        INPUT_TYPE_MAP.put(
                "textAutoComplete",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        INPUT_TYPE_MAP.put(
                "textMultiLine", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        INPUT_TYPE_MAP.put(
                "textImeMultiLine",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE);
        INPUT_TYPE_MAP.put(
                "textNoSuggestions",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        INPUT_TYPE_MAP.put("textUri", InputType.TYPE_TEXT_VARIATION_URI);
        INPUT_TYPE_MAP.put("textEmailAddress", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        INPUT_TYPE_MAP.put("textEmailSubject", InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT);
        INPUT_TYPE_MAP.put("textShortMessage", InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
        INPUT_TYPE_MAP.put("textLongMessage", InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE);
        INPUT_TYPE_MAP.put("textPersonName", InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        INPUT_TYPE_MAP.put("textPostalAddress", InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        INPUT_TYPE_MAP.put("textPassword", InputType.TYPE_TEXT_VARIATION_PASSWORD);
        INPUT_TYPE_MAP.put("textVisiblePassword", InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        INPUT_TYPE_MAP.put("textWebEditText", InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);
        INPUT_TYPE_MAP.put("textFilter", InputType.TYPE_TEXT_VARIATION_FILTER);
        INPUT_TYPE_MAP.put("textPhonetic", InputType.TYPE_TEXT_VARIATION_PHONETIC);
        INPUT_TYPE_MAP.put("textWebEmailAddress", InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
        INPUT_TYPE_MAP.put("textWebPassword", InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        INPUT_TYPE_MAP.put("number", InputType.TYPE_CLASS_NUMBER);
        INPUT_TYPE_MAP.put(
                "numberSigned", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        INPUT_TYPE_MAP.put(
                "numberDecimal", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        INPUT_TYPE_MAP.put(
                "numberPassword",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        INPUT_TYPE_MAP.put("phone", InputType.TYPE_CLASS_PHONE);
        INPUT_TYPE_MAP.put("datetime", InputType.TYPE_CLASS_DATETIME);
        INPUT_TYPE_MAP.put(
                "date", InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        INPUT_TYPE_MAP.put(
                "time", InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.EDIT_TEXT,
                SdkConstants.AUTO_COMPLETE_TEXT_VIEW,
                SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW,
                EXTRACT_EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isTextField(element)) {
            return;
        }

        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);

        if (inputType == null || inputType.isEmpty()) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        String idName = getIdName(id);
        if (idName == null) {
            return;
        }

        int type = parseInputType(inputType);
        if (type == -1) {
            return;
        }

        if (idName.contains("email")) {
            if (!isEmailInputType(type)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(
                                element.getAttributeNodeNS(
                                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE)),
                        "The id of this field suggests it should accept an email address; "
                                + "add `textEmailAddress` (or `textWebEmailAddress`) to its `inputType`");
            }
        } else if (idName.contains("phone")) {
            if (!isPhoneInputType(type)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(
                                element.getAttributeNodeNS(
                                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE)),
                        "The id of this field suggests it should accept a phone number; "
                                + "set its `inputType` to `phone`");
            }
        }
    }

    private static boolean isTextField(Element element) {
        String tag = element.getTagName();
        return tag.equals(SdkConstants.EDIT_TEXT)
                || tag.equals(SdkConstants.AUTO_COMPLETE_TEXT_VIEW)
                || tag.equals(SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW)
                || tag.equals(EXTRACT_EDIT_TEXT)
                || tag.endsWith("." + SdkConstants.EDIT_TEXT)
                || tag.endsWith("." + SdkConstants.AUTO_COMPLETE_TEXT_VIEW)
                || tag.endsWith("." + SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW)
                || tag.endsWith("." + EXTRACT_EDIT_TEXT);
    }

    private static String getIdName(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int index = id.lastIndexOf('/');
        String name = index >= 0 ? id.substring(index + 1) : id;
        return name.toLowerCase(Locale.US);
    }

    private static int parseInputType(String inputType) {
        int result = 0;
        for (String token : inputType.split("\\|")) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            Integer value = INPUT_TYPE_MAP.get(token);
            if (value != null) {
                result |= value;
            } else {
                try {
                    result |= Integer.decode(token);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return result;
    }

    private static boolean isPhoneInputType(int type) {
        return (type & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE;
    }

    private static boolean isEmailInputType(int type) {
        if ((type & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        int variation = type & InputType.TYPE_MASK_VARIATION;
        return variation
                        == (InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                                & InputType.TYPE_MASK_VARIATION)
                || variation
                        == (InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                                & InputType.TYPE_MASK_VARIATION)
                || variation
                        == (InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT
                                & InputType.TYPE_MASK_VARIATION);
    }
}