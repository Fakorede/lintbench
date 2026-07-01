package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class TextFieldDetector extends LayoutDetector {
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
        return Arrays.asList(
            "EditText",
            "android.widget.EditText",
            "AutoCompleteTextView",
            "android.widget.AutoCompleteTextView",
            "MultiAutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView",
            "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        String inputType = inputTypeAttr != null ? inputTypeAttr.getValue() : null;

        if (inputType == null || inputType.isEmpty()) {
            context.report(ISSUE, context.getLocation(element),
                "This text field does not specify an `inputType`");
            return;
        }

        if (inputType.startsWith("@")) {
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue();
        int slash = id.lastIndexOf('/');
        if (slash != -1) {
            id = id.substring(slash + 1);
        }
        String lowerId = id.toLowerCase(Locale.US);

        Map<String, String> hints = new LinkedHashMap<>();
        hints.put("phone", "phone");
        hints.put("email", "email");
        hints.put("password", "password");
        hints.put("date", "date");
        hints.put("time", "time");
        hints.put("number", "number");
        hints.put("uri", "uri");
        hints.put("url", "uri");
        hints.put("web", "uri");
        hints.put("postal", "postalAddress");
        hints.put("address", "postalAddress");

        for (Map.Entry<String, String> entry : hints.entrySet()) {
            if (lowerId.contains(entry.getKey())) {
                String expected = entry.getValue();
                if (!inputType.contains(expected)) {
                    String message = String.format(
                        "The id `%1$s` suggests this field should be of type `%2$s`, " +
                        "but the inputType is `%3$s`",
                        id, expected, inputType);
                    context.report(ISSUE, context.getLocation(inputTypeAttr), message);
                }
                break;
            }
        }
    }
}