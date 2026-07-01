package com.android.tools.lint.checks;

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_ID = "id";

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
                            TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "android.support.design.widget.TextInputEditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String inputType = inputTypeAttr != null ? inputTypeAttr.getValue() : null;

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        String idName = null;
        if (id != null && !id.isEmpty()) {
            if (id.startsWith("@+id/")) {
                idName = id.substring(5);
            } else if (id.startsWith("@id/")) {
                idName = id.substring(4);
            } else {
                idName = id;
            }
            idName = idName.toLowerCase(Locale.US);
        }

        if (inputType == null || inputType.isEmpty()) {
            if (idName != null) {
                if (idName.contains("phone")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "This text field is name-hinted as a phone number, but does not configure `inputType=\"phone\"`");
                    return;
                } else if (idName.contains("password")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "This text field is name-hinted as a password, but does not configure `inputType=\"textPassword\"`");
                    return;
                } else if (idName.contains("email")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "This text field is name-hinted as an email address, but does not configure `inputType=\"textEmailAddress\"`");
                    return;
                }
            }
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `inputType` attribute");
        } else {
            if (idName != null) {
                if (idName.contains("phone") && !inputType.contains("phone")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getLocation(inputTypeAttr),
                            "This text field is name-hinted as a phone number, but does not configure `inputType=\"phone\"`");
                } else if (idName.contains("password") && !inputType.contains("password")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getLocation(inputTypeAttr),
                            "This text field is name-hinted as a password, but does not configure `inputType=\"textPassword\"`");
                } else if (idName.contains("email") && !inputType.contains("email")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getLocation(inputTypeAttr),
                            "This text field is name-hinted as an email address, but does not configure `inputType=\"textEmailAddress\"`");
                }
            }
        }
    }
}