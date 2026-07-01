package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType",
            "Providing an `inputType` attribute on a text field improves usability "
                    + "because depending on the data to be input, optimized keyboards can be shown "
                    + "to the user (such as just digits and parentheses for a phone number).\n"
                    + "\n"
                    + "The lint detector also looks at the `id` of the view, and if the id offers a "
                    + "hint of the purpose of the field (for example, the `id` contains the phrase "
                    + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                    + "the corresponding type attributes.\n"
                    + "\n"
                    + "If you really want to keep the text field generic, you can suppress this warning "
                    + "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.LAYOUT_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (!tagName.equals(EDIT_TEXT)
                && !tagName.equals(AUTO_COMPLETE_TEXT_VIEW)
                && !tagName.equals(MULTI_AUTO_COMPLETE_TEXT_VIEW)
                && !tagName.equals("com.google.android.material.textfield.TextInputEditText")
                && !tagName.endsWith("EditText")) {
            return;
        }

        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String inputType = inputTypeAttr != null ? inputTypeAttr.getValue() : null;

        if (inputType == null || inputType.isEmpty()) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "Missing `inputType` attribute");
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            String idName = id.substring(id.indexOf('/') + 1).toLowerCase();

            if (idName.contains("email")) {
                if (!inputType.contains("textEmailAddress")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field is named as if it's an email address, but `inputType` does not specify `textEmailAddress`");
                }
            } else if (idName.contains("phone")) {
                if (!inputType.contains("phone")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field is named as if it's a phone number, but `inputType` does not specify `phone`");
                }
            } else if (idName.contains("password")) {
                if (!inputType.toLowerCase().contains("password")) {
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            "This text field is named as if it's a password, but `inputType` does not specify a password type");
                }
            }
        }
    }
}