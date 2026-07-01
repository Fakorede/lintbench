package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.TEXT_INPUT_EDIT_TEXT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TextFieldDetector extends ResourceXmlDetector {
    private static final Implementation IMPLEMENTATION = new Implementation(
            TextFieldDetector.class,
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability "
                    + "because depending on the data to be input, optimized keyboards can be shown "
                    + "to the user (such as just digits and parentheses for a phone number). "
                    + "The lint detector also looks at the `id` of the view, and if the `id` offers "
                    + "a hint of the purpose of the field (for example, the `id` contains the phrase "
                    + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                    + "the corresponding type attributes. If you really want to keep the text field "
                    + "generic, you can suppress this warning by setting `inputType=\\\"text\\\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            IMPLEMENTATION
    );

    private static final Collection<String> TEXT_FIELD_TAGS = Arrays.asList(
            EDIT_TEXT,
            AUTO_COMPLETE_TEXT_VIEW,
            MULTI_AUTO_COMPLETE_TEXT_VIEW,
            TEXT_INPUT_EDIT_TEXT
    );

    private static final String NEW_ID_PREFIX = "@+id/";
    private static final String ID_PREFIX = "@id/";

    private static final Map<String, String> ID_HINT_TO_INPUT_TYPE = new HashMap<>();
    static {
        ID_HINT_TO_INPUT_TYPE.put("email", "textEmailAddress");
        ID_HINT_TO_INPUT_TYPE.put("e-mail", "textEmailAddress");
        ID_HINT_TO_INPUT_TYPE.put("mail", "textEmailAddress");
        ID_HINT_TO_INPUT_TYPE.put("phone", "phone");
        ID_HINT_TO_INPUT_TYPE.put("tel", "phone");
        ID_HINT_TO_INPUT_TYPE.put("password", "textPassword");
        ID_HINT_TO_INPUT_TYPE.put("passwd", "textPassword");
        ID_HINT_TO_INPUT_TYPE.put("pwd", "textPassword");
        ID_HINT_TO_INPUT_TYPE.put("url", "textUri");
        ID_HINT_TO_INPUT_TYPE.put("link", "textUri");
        ID_HINT_TO_INPUT_TYPE.put("website", "textUri");
        ID_HINT_TO_INPUT_TYPE.put("date", "date");
        ID_HINT_TO_INPUT_TYPE.put("time", "time");
        ID_HINT_TO_INPUT_TYPE.put("number", "number");
        ID_HINT_TO_INPUT_TYPE.put("amount", "number");
        ID_HINT_TO_INPUT_TYPE.put("card", "number");
        ID_HINT_TO_INPUT_TYPE.put("name", "textPersonName");
        ID_HINT_TO_INPUT_TYPE.put("first", "textPersonName");
        ID_HINT_TO_INPUT_TYPE.put("last", "textPersonName");
        ID_HINT_TO_INPUT_TYPE.put("person", "textPersonName");
        ID_HINT_TO_INPUT_TYPE.put("user", "textPersonName");
        ID_HINT_TO_INPUT_TYPE.put("username", "textPersonName");
        ID_HINT_TO_INPUT_TYPE.put("address", "textPostalAddress");
        ID_HINT_TO_INPUT_TYPE.put("zip", "textPostalAddress");
        ID_HINT_TO_INPUT_TYPE.put("postal", "textPostalAddress");
        ID_HINT_TO_INPUT_TYPE.put("subject", "textShortMessage");
        ID_HINT_TO_INPUT_TYPE.put("message", "textShortMessage");
        ID_HINT_TO_INPUT_TYPE.put("body", "textShortMessage");
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return TEXT_FIELD_TAGS;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String idValue = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        String idName = stripIdPrefix(idValue);
        String hintType = idName != null ? getHintedInputType(idName) : null;

        if (inputTypeAttr == null) {
            if (hintType != null) {
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field is missing an `android:inputType` attribute; "
                                + "its `id` suggests that the type should include `"
                                + hintType + "`");
            } else {
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field is missing an `android:inputType` attribute");
            }
            return;
        }

        String inputType = inputTypeAttr.getValue();
        if (inputType == null || inputType.equalsIgnoreCase("text")) {
            return;
        }

        if (hintType != null && !inputType.toLowerCase(Locale.ROOT).contains(
                hintType.toLowerCase(Locale.ROOT))) {
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                    "The `android:inputType` attribute should include `"
                            + hintType + "` for this field");
        }
    }

    private static String stripIdPrefix(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        if (id.startsWith(NEW_ID_PREFIX)) {
            return id.substring(NEW_ID_PREFIX.length());
        }
        if (id.startsWith(ID_PREFIX)) {
            return id.substring(ID_PREFIX.length());
        }
        return id;
    }

    private static String getHintedInputType(@NonNull String id) {
        String lowerId = id.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : ID_HINT_TO_INPUT_TYPE.entrySet()) {
            if (lowerId.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}