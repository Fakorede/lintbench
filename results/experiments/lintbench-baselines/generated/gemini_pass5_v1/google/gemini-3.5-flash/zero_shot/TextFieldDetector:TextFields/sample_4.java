package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType or useful inputType",
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
            new Implementation(
                    TextFieldDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    public TextFieldDetector() {
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (!tagName.equals("EditText") && !tagName.endsWith(".EditText") && !tagName.endsWith("EditText")) {
            return;
        }

        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String inputType = inputTypeAttr != null ? inputTypeAttr.getValue() : "";

        if (inputType.isEmpty()) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "Missing `inputType` attribute");
            return;
        }

        // Setting inputType="text" suppresses further specific ID-based checks
        if ("text".equals(inputType)) {
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            String idName = id.substring(id.lastIndexOf('/') + 1).toLowerCase(Locale.US);
            String inputTypeLower = inputType.toLowerCase(Locale.US);

            if (idName.contains("phone") || idName.contains("tel")) {
                if (!inputTypeLower.contains("phone")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            String.format("This text field is named like a phone number, but the `inputType` is `%s` instead of `phone`?", inputType));
                }
            } else if (idName.contains("email")) {
                if (!inputTypeLower.contains("email")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            String.format("This text field is named like an email address, but the `inputType` is `%s` instead of `textEmailAddress`?", inputType));
                }
            } else if (idName.contains("password") || idName.contains("pin")) {
                if (!inputTypeLower.contains("password")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            String.format("This text field is named like a password, but the `inputType` does not contain a password type (was `%s`)", inputType));
                }
            } else if (idName.contains("number") || idName.contains("numeric") || idName.contains("amount")) {
                if (!inputTypeLower.contains("number") && !inputTypeLower.contains("phone") &&
                    !inputTypeLower.contains("date") && !inputTypeLower.contains("time")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            String.format("This text field is named like a number, but the `inputType` is `%s`", inputType));
                }
            } else if (idName.contains("postal") || idName.contains("zip")) {
                if (!inputTypeLower.contains("number") && !inputTypeLower.contains("postal")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            String.format("This text field is named like a postal code, but the `inputType` is `%s`", inputType));
                }
            }
        }
    }
}