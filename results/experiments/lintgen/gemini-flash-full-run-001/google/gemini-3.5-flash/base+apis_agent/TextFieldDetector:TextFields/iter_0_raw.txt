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
            new Implementation(TextFieldDetector.class, Scope.LAYOUT_RESOURCE_FILES)
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
            String idName = id.substring(id.indexOf('/') + 1).toLowerCase(Locale.US);

            if (idName.contains("phone") || idName.contains("tel")) {
                if (!inputType.contains("phone")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is a phone number, but does not specify `inputType=\"phone\"`");
                }
            } else if (idName.contains("email")) {
                if (!inputType.contains("textEmailAddress")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is an email address, but does not specify `inputType=\"textEmailAddress\"`");
                }
            } else if (idName.contains("password")) {
                if (!inputType.contains("Password") && !inputType.contains("password")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is a password, but does not specify `inputType=\"textPassword\"` or similar");
                }
            } else if (idName.contains("url") || idName.contains("uri") || idName.contains("website")) {
                if (!inputType.contains("textUri")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is a URL, but does not specify `inputType=\"textUri\"`");
                }
            } else if (idName.contains("postal") || idName.contains("zip")) {
                if (!inputType.contains("postalAddress") && !inputType.contains("number")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is a postal code, but does not specify `inputType=\"postalAddress\"` or `inputType=\"number\"`");
                }
            } else if (idName.contains("numeric") || idName.contains("number")) {
                if (!inputType.contains("number") && !inputType.contains("phone") && !inputType.contains("date")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field has an id suggesting it is numeric, but does not specify `inputType=\"number\"` or similar");
                }
            }
        }
    }
}