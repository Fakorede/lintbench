package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.Locale;

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

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("EditText");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String ns = "http://schemas.android.com/apk/res/android";
        String inputType = element.getAttributeNS(ns, "inputType");
        
        // If inputType is already specified, no need to warn
        if (inputType != null && !inputType.isEmpty()) {
            return;
        }

        String id = element.getAttributeNS(ns, "id");
        String suggestedType = null;
        
        if (id != null && !id.isEmpty()) {
            String lowerId = id.toLowerCase(Locale.US);
            if (lowerId.contains("phone")) {
                suggestedType = "phone";
            } else if (lowerId.contains("email")) {
                suggestedType = "textEmailAddress";
            } else if (lowerId.contains("password")) {
                suggestedType = "textPassword";
            } else if (lowerId.contains("number")) {
                suggestedType = "number";
            } else if (lowerId.contains("url")) {
                suggestedType = "textUri";
            } else if (lowerId.contains("date")) {
                suggestedType = "date";
            } else if (lowerId.contains("time")) {
                suggestedType = "time";
            }
        }

        String message;
        if (suggestedType != null) {
            message = String.format(
                    "This text field does not specify an `inputType` or a `hint`. "
                    + "Based on the ID, consider adding `android:inputType=\"%s\"`.",
                    suggestedType);
        } else {
            message = "This text field does not specify an `inputType` or a `hint`.";
        }

        context.report(ISSUE, element, context.getLocation(element), message);
    }
}