package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String EDIT_TEXT = "EditText";

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType or useful inputType",
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because depending on the data to be input, optimized keyboards "
                            + "can be shown to the user (such as just digits and parentheses "
                            + "for a phone number).\n"
                            + "\n"
                            + "The lint detector also looks at the `id` of the view, and if the "
                            + "id offers a hint of the purpose of the field (for example, the "
                            + "`id` contains the phrase `phone` or `email`), then lint will also "
                            + "ensure that the `inputType` contains the corresponding type attributes.\n"
                            + "\n"
                            + "If you really want to keep the text field generic, you can suppress "
                            + "this warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        org.w3c.dom.Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String inputType = inputTypeAttr != null ? inputTypeAttr.getValue() : null;

        if (inputType == null || inputType.isEmpty()) {
            if (element.hasAttributeNS(ANDROID_URI, "password")
                    || element.hasAttributeNS(ANDROID_URI, "numeric")
                    || element.hasAttributeNS(ANDROID_URI, "phoneNumber")) {
                return;
            }
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `inputType` attribute");
            return;
        }

        org.w3c.dom.Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            String idName = id.substring(id.indexOf('/') + 1).toLowerCase(java.util.Locale.US);

            if (idName.contains("phone")) {
                if (!inputType.contains("phone")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getValueLocation(inputTypeAttr),
                            "Input type should be phone-related (e.g. `phone`) as the ID suggests a phone number");
                }
            } else if (idName.contains("email")) {
                if (!inputType.toLowerCase(java.util.Locale.US).contains("email")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getValueLocation(inputTypeAttr),
                            "Input type should be email-related (e.g. `textEmailAddress`) as the ID suggests an email address");
                }
            } else if (idName.contains("password")) {
                if (!inputType.toLowerCase(java.util.Locale.US).contains("password")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getValueLocation(inputTypeAttr),
                            "Input type should be password-related (e.g. `textPassword`) as the ID suggests a password");
                }
            }
        }
    }
}