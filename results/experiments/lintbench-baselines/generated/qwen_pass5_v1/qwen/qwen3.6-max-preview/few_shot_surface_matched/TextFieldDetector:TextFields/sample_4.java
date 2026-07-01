package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType",
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number). "
                            + "The lint detector also looks at the `id` of the view, and if the id offers a "
                            + "hint of the purpose of the field (for example, the `id` contains the phrase "
                            + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                            + "the corresponding type attributes. "
                            + "If you really want to keep the text field generic, you can suppress this warning "
                            + "by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (!id.isEmpty() && !inputType.startsWith("@") && !inputType.startsWith("?")) {
            String idLower = id.toLowerCase();
            checkHint(context, element, idLower, inputType, "phone", "phone");
            checkHint(context, element, idLower, inputType, "email", "textEmailAddress");
            checkHint(context, element, idLower, inputType, "password", "textPassword");
            checkHint(context, element, idLower, inputType, "number", "number");
            checkHint(context, element, idLower, inputType, "date", "date");
            checkHint(context, element, idLower, inputType, "time", "time");
            checkHint(context, element, idLower, inputType, "uri", "textUri");
            checkHint(context, element, idLower, inputType, "web", "textUri");
        }
    }

    private void checkHint(@NonNull XmlContext context, @NonNull Element element,
                           @NonNull String idLower, @NonNull String inputType,
                           @NonNull String hint, @NonNull String expectedType) {
        if (idLower.contains(hint) && !inputType.contains(expectedType)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id suggests this field is for " + hint + ", but the inputType does not match");
        }
    }
}