package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because depending on the data to be input, optimized keyboards "
                            + "can be shown to the user (such as just digits and parentheses "
                            + "for a phone number).\n\n"
                            + "The lint detector also looks at the `id` of the view, and if "
                            + "the id offers a hint of the purpose of the field (for example, "
                            + "the `id` contains the phrase `phone` or `email`), then lint "
                            + "will also ensure that the `inputType` contains the corresponding "
                            + "type attributes.\n\n"
                            + "If you really want to keep the text field generic, you can "
                            + "suppress this warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "android.widget.EditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String inputType = inputTypeAttr != null ? inputTypeAttr.getValue() : "";

        if (inputType.isEmpty()) {
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

        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            String idName = idValue.substring(idValue.indexOf('/') + 1).toLowerCase(Locale.US);

            if (idName.contains("phone")) {
                if (!inputType.contains("phone")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getValueLocation(inputTypeAttr),
                            "The field name contains `phone`, but `inputType` does not include `phone`"
                    );
                }
            } else if (idName.contains("email")) {
                if (!inputType.contains("textEmailAddress") && !inputType.contains("textWebEmailAddress")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getValueLocation(inputTypeAttr),
                            "The field name contains `email`, but `inputType` does not include `textEmailAddress`"
                    );
                }
            } else if (idName.contains("password")) {
                String lowerInputType = inputType.toLowerCase(Locale.US);
                if (!lowerInputType.contains("password")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getValueLocation(inputTypeAttr),
                            "The field name contains `password`, but `inputType` does not include password types"
                    );
                }
            }
        }
    }
}