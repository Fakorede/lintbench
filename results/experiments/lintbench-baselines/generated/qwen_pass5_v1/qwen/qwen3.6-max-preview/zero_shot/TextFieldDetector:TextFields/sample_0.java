package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.EDIT_TEXT;

public class TextFieldDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType",
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
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);

        if (inputType == null || inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        // Skip resource references as they cannot be statically analyzed reliably
        if (inputType.startsWith("@")) {
            return;
        }

        // Explicit generic text suppresses the warning per specification
        if ("text".equals(inputType)) {
            return;
        }

        if (id != null && !id.isEmpty()) {
            String lowerId = id.toLowerCase();
            String lowerInputType = inputType.toLowerCase();

            if (lowerId.contains("phone") && !lowerInputType.contains("phone")) {
                context.report(ISSUE, element, context.getLocation(element),
                        "The id suggests this is a phone number field, but the `inputType` does not include `phone`");
            } else if (lowerId.contains("email") && !lowerInputType.contains("email")) {
                context.report(ISSUE, element, context.getLocation(element),
                        "The id suggests this is an email field, but the `inputType` does not include `email`");
            }
        }
    }
}