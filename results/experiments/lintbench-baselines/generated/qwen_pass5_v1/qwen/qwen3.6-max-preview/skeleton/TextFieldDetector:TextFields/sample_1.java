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
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class TextFieldDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
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
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "android.widget.EditText",
                "TextView",
                "android.widget.TextView"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        boolean isEditText = tagName.equals("EditText") || tagName.equals("android.widget.EditText");

        String inputType = element.getAttributeNS(ANDROID_URI, "inputType");
        String id = element.getAttributeNS(ANDROID_URI, "id");

        if (isEditText && (inputType == null || inputType.isEmpty())) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `inputType` attribute. Providing an `inputType` improves usability by showing an optimized keyboard. " +
                    "If you want to keep it generic, set `inputType=\"text\"`."
            );
            return;
        }

        if (inputType != null && !inputType.isEmpty() && !inputType.startsWith("@") && id != null && !id.isEmpty()) {
            String cleanId = id.replaceAll("^@\\+?id/", "").toLowerCase(Locale.US);
            String lowerInputType = inputType.toLowerCase(Locale.US);
            Node inputTypeNode = element.getAttributeNodeNS(ANDROID_URI, "inputType");

            if (cleanId.contains("phone") && !lowerInputType.contains("phone")) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(inputTypeNode),
                        "The id suggests this field is for a phone number, but the inputType does not contain \"phone\"."
                );
            } else if (cleanId.contains("email") && !lowerInputType.contains("email")) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(inputTypeNode),
                        "The id suggests this field is for an email address, but the inputType does not contain \"email\"."
                );
            } else if (cleanId.contains("password") && !lowerInputType.contains("password")) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(inputTypeNode),
                        "The id suggests this field is for a password, but the inputType does not contain \"password\"."
                );
            }
        }
    }
}