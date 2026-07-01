package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number).\n\n"
                            + "The lint detector also looks at the `id` of the view, and if the id offers a "
                            + "hint of the purpose of the field (for example, the `id` contains the phrase "
                            + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                            + "the corresponding type attributes.\n\n"
                            + "If you really want to keep the text field generic, you can suppress this warning "
                            + "by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            TextFieldDetector.class, Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW,
                "TextInputEditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputType == null || inputType.isEmpty()) {
            if (element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "password")
                    || element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "numeric")
                    || element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "phoneNumber")
                    || element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "digits")
                    || element.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, "inputMethod")) {
                return;
            }
            context.report(ISSUE, element, context.getNameLocation(element),
                    "Missing `inputType` attribute");
        } else {
            String id = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, ATTR_ID);
            if (id != null && !id.isEmpty()) {
                String idName = id.substring(id.indexOf('/') + 1).toLowerCase();
                Attr inputTypeAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, ATTR_INPUT_TYPE);
                if (inputTypeAttr != null) {
                    if (idName.contains("phone") || idName.contains("tele")) {
                        if (!inputType.contains("phone")) {
                            context.report(ISSUE, inputTypeAttr, context.getValueLocation(inputTypeAttr),
                                    "Choose an input type that supports phone numbers (e.g. `phone`)");
                        }
                    } else if (idName.contains("email")) {
                        if (!inputType.contains("Email")) {
                            context.report(ISSUE, inputTypeAttr, context.getValueLocation(inputTypeAttr),
                                    "Choose an input type that supports email addresses (e.g. `textEmailAddress`)");
                        }
                    } else if (idName.contains("password")) {
                        if (!inputType.contains("Password")) {
                            context.report(ISSUE, inputTypeAttr, context.getValueLocation(inputTypeAttr),
                                    "Choose an input type that supports passwords (e.g. `textPassword`)");
                        }
                    } else if (idName.contains("uri") || idName.contains("url")) {
                        if (!inputType.contains("Uri")) {
                            context.report(ISSUE, inputTypeAttr, context.getValueLocation(inputTypeAttr),
                                    "Choose an input type that supports URLs (e.g. `textUri`)");
                        }
                    }
                }
            }
        }
    }
}