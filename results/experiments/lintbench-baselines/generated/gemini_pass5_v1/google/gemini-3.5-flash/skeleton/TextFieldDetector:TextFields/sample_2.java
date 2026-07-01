package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

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
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_ID = "id";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("EditText", "AutoCompleteTextView", "MultiAutoCompleteTextView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (attribute == null) {
            // Check for deprecated attributes that serve a similar purpose
            if (element.hasAttributeNS(ANDROID_URI, "password")
                    || element.hasAttributeNS(ANDROID_URI, "numeric")
                    || element.hasAttributeNS(ANDROID_URI, "phoneNumber")
                    || element.hasAttributeNS(ANDROID_URI, "inputMethod")) {
                return;
            }
            context.report(ISSUE, element, context.getNameLocation(element),
                    "Missing `inputType` attribute");
            return;
        }

        String inputType = attribute.getValue();
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = id.substring(id.lastIndexOf('/') + 1);
            if (idName.contains("email") || idName.contains("Email")) {
                if (!inputType.contains("textEmailAddress")) {
                    context.report(ISSUE, attribute, context.getValueLocation(attribute),
                            "An email text field should use `inputType=\"textEmailAddress\"`");
                }
            } else if (idName.contains("phone") || idName.contains("Phone")) {
                if (!inputType.contains("phone")) {
                    context.report(ISSUE, attribute, context.getValueLocation(attribute),
                            "A phone number field should use `inputType=\"phone\"`");
                }
            } else if (idName.contains("password") || idName.contains("Password")) {
                if (!inputType.contains("Password") && !inputType.contains("password")) {
                    context.report(ISSUE, attribute, context.getValueLocation(attribute),
                            "A password field should use `inputType=\"textPassword\"` (or similar)");
                }
            } else if (idName.contains("number") || idName.contains("Number")
                    || idName.contains("numeric") || idName.contains("Numeric")) {
                if (!inputType.contains("number") && !inputType.contains("phone")
                        && !inputType.contains("date") && !inputType.contains("time")) {
                    context.report(ISSUE, attribute, context.getValueLocation(attribute),
                            "A numeric field should use `inputType=\"number\"` (or similar)");
                }
            }
        }
    }
}