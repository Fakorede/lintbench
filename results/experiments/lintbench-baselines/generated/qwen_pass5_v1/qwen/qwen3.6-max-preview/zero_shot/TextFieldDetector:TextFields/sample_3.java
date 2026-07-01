package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class TextFieldDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "TextFields",
        "Missing inputType",
        "Providing an `inputType` attribute on a text field improves usability because depending on the data to be input, optimized keyboards can be shown to the user (such as just digits and parentheses for a phone number).\n\n" +
        "The lint detector also looks at the `id` of the view, and if the id offers a hint of the purpose of the field (for example, the `id` contains the phrase `phone` or `email`), then lint will also ensure that the `inputType` contains the corresponding type attributes.\n\n" +
        "If you really want to keep the text field generic, you can suppress this warning by setting `inputType=\"text\"`.",
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
            "EditText",
            "android.widget.EditText",
            "AutoCompleteTextView",
            "android.widget.AutoCompleteTextView",
            "TextInputEditText",
            "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, "inputType").trim();
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, "id").trim();

        if (inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                "This text field does not specify an `inputType`");
            return;
        }

        if (inputType.startsWith("@") || inputType.startsWith("?")) {
            return;
        }

        if (!id.isEmpty()) {
            String idName = id.replaceFirst("^@\\+?id/", "");
            String lowerId = idName.toLowerCase();
            String lowerInputType = inputType.toLowerCase();

            String message = null;
            if (lowerId.contains("phone") && !lowerInputType.contains("phone")) {
                message = "The id suggests this is a phone number, but the inputType is not `phone`";
            } else if (lowerId.contains("email") && !lowerInputType.contains("email")) {
                message = "The id suggests this is an email address, but the inputType is not `textEmailAddress`";
            } else if (lowerId.contains("password") && !lowerInputType.contains("password")) {
                message = "The id suggests this is a password, but the inputType does not contain `password`";
            } else if (lowerId.contains("number") && !lowerInputType.contains("number")) {
                message = "The id suggests this is a number, but the inputType is not `number`";
            } else if ((lowerId.contains("date") || lowerId.contains("time")) &&
                       !lowerInputType.contains("date") && !lowerInputType.contains("time")) {
                message = "The id suggests this is a date/time, but the inputType does not match";
            } else if ((lowerId.contains("url") || lowerId.contains("web") || lowerId.contains("uri")) &&
                       !lowerInputType.contains("uri")) {
                message = "The id suggests this is a URL, but the inputType is not `textUri`";
            }

            if (message != null) {
                context.report(ISSUE, element,
                    context.getLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "inputType")),
                    message);
            }
        }
    }
}