package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
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

public class TextFieldDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType or useful inputType",
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
                SdkConstants.EDIT_TEXT,
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView",
                "android.support.design.widget.TextInputEditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (inputTypeAttr == null) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "Missing `inputType` attribute");
            return;
        }

        String inputType = inputTypeAttr.getValue();
        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue();
        String idName = id;
        int index = id.indexOf('/');
        if (index != -1) {
            idName = id.substring(index + 1);
        }
        idName = idName.toLowerCase(Locale.US);

        if (idName.contains("phone") || idName.contains("tele")) {
            if (!inputType.contains("phone")) {
                context.report(ISSUE, inputTypeAttr, context.getValueLocation(inputTypeAttr),
                        "Based on the ID of this text field, it should have `inputType=\"phone\"` or similar");
            }
        } else if (idName.contains("email")) {
            if (!inputType.contains("textEmailAddress") && !inputType.contains("textWebEmailAddress")) {
                context.report(ISSUE, inputTypeAttr, context.getValueLocation(inputTypeAttr),
                        "Based on the ID of this text field, it should have `inputType=\"textEmailAddress\"` or similar");
            }
        } else if (idName.contains("password") || idName.contains("pin")) {
            if (!inputType.contains("Password")) {
                context.report(ISSUE, inputTypeAttr, context.getValueLocation(inputTypeAttr),
                        "Based on the ID of this text field, it should have `inputType=\"textPassword\"` or similar");
            }
        } else if (idName.contains("number") || idName.contains("numeric") || idName.contains("amount")) {
            if (!inputType.contains("number") && !inputType.contains("phone") && !inputType.contains("date") && !inputType.contains("time")) {
                context.report(ISSUE, inputTypeAttr, context.getValueLocation(inputTypeAttr),
                        "Based on the ID of this text field, it should have `inputType=\"number\"` or similar");
            }
        }
    }
}