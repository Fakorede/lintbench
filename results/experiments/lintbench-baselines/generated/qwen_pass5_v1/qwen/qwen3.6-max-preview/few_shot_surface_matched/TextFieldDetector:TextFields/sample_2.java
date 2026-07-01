package com.android.tools.lint.checks;

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType",
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
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("EditText");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, "inputType");
        String id = element.getAttributeNS(ANDROID_URI, "id");

        if (inputType.isEmpty()) {
            String hint = getHintFromId(id);
            String message;
            if (hint != null) {
                message = String.format(
                        "This text field does not specify an `inputType` or a hint. "
                                + "Based on the ID `%s`, consider adding `inputType=\"%s\"`.",
                        id, hint);
            } else {
                message = "This text field does not specify an `inputType` or a hint. "
                        + "Consider adding `inputType=\"text\"` or a hint for better usability.";
            }
            context.report(ISSUE, element, context.getLocation(element), message);
        } else if (!id.isEmpty()) {
            String expected = getHintFromId(id);
            if (expected != null && !inputType.contains(expected)) {
                context.report(ISSUE, element, context.getLocation(element),
                        String.format("The ID `%s` suggests this field is for %s, but the `inputType` "
                                        + "is `%s`. Consider adding `%s` to the `inputType`.",
                                id, expected, inputType, expected));
            }
        }
    }

    private static String getHintFromId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String lower = id.toLowerCase();
        if (lower.contains("phone")) return "phone";
        if (lower.contains("email")) return "textEmailAddress";
        if (lower.contains("password")) return "textPassword";
        if (lower.contains("number")) return "number";
        if (lower.contains("date")) return "date";
        if (lower.contains("time")) return "time";
        if (lower.contains("uri") || lower.contains("web")) return "textUri";
        return null;
    }
}