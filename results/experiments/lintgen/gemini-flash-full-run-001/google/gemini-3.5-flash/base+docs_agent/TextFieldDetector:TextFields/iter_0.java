package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType or useful inputType",
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
                    TextFieldDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.EDIT_TEXT,
                "android.widget.EditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (inputType == null || inputType.isEmpty()) {
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "numeric")
                    || element.hasAttributeNS(SdkConstants.ANDROID_URI, "phoneNumber")
                    || element.hasAttributeNS(SdkConstants.ANDROID_URI, "password")
                    || element.hasAttributeNS(SdkConstants.ANDROID_URI, "inputMethod")) {
                return;
            }

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `inputType` attribute"
            );
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = id.substring(id.lastIndexOf('/') + 1);
            String idLower = idName.toLowerCase(Locale.US);

            if (idLower.contains("email")) {
                if (!inputType.contains("textEmailAddress") && !inputType.contains("textWebEmailAddress")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getValueLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE)),
                            "This text field is named like an email field, but does not use the corresponding `inputType` (such as `textEmailAddress`)"
                    );
                }
            } else if (idLower.contains("phone")) {
                if (!inputType.contains("phone")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getValueLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE)),
                            "This text field is named like a phone number field, but does not use the corresponding `inputType` (`phone`)"
                    );
                }
            } else if (idLower.contains("password")) {
                if (!inputType.contains("Password") && !inputType.contains("password")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getValueLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE)),
                            "This text field is named like a password field, but does not use the corresponding `inputType` (such as `textPassword`)"
                    );
                }
            } else if (idLower.contains("url") || idLower.contains("uri") || idLower.contains("website")) {
                if (!inputType.contains("textUri")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getValueLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE)),
                            "This text field is named like a URL field, but does not use the corresponding `inputType` (`textUri`)"
                    );
                }
            } else if (idLower.contains("search")) {
                if (!inputType.contains("textSearch")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getValueLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE)),
                            "This text field is named like a search field, but does not use the corresponding `inputType` (`textSearch`)"
                    );
                }
            } else if (idLower.contains("date")) {
                if (!inputType.contains("date") && !inputType.contains("time") && !inputType.contains("datetime")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getValueLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE)),
                            "This text field is named like a date field, but does not use the corresponding `inputType` (`date` or `datetime`)"
                    );
                }
            } else if (idLower.contains("number") || idLower.contains("numeric")) {
                if (!inputType.contains("number") && !inputType.contains("phone") && !inputType.contains("date")
                        && !inputType.contains("time") && !inputType.contains("decimal") && !inputType.contains("signed")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getValueLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE)),
                            "This text field is named like a numeric field, but does not use the corresponding `inputType` (such as `number`)"
                    );
                }
            }
        }
    }
}