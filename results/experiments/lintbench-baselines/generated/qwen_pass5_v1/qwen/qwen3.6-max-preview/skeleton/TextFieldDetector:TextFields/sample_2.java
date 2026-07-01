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
import org.w3c.dom.Element;

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

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttribute("android:inputType");
        if (inputType == null || inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        if (inputType.startsWith("@")) {
            return;
        }

        String id = element.getAttribute("android:id");
        if (id == null || id.isEmpty()) {
            return;
        }

        String idName = id.substring(id.lastIndexOf('/') + 1).toLowerCase();
        String expectedType = null;

        if (idName.contains("phone")) {
            expectedType = "phone";
        } else if (idName.contains("email")) {
            expectedType = "textEmailAddress";
        } else if (idName.contains("password")) {
            expectedType = "textPassword";
        } else if (idName.contains("number")) {
            expectedType = "number";
        } else if (idName.contains("uri") || idName.contains("web")) {
            expectedType = "textUri";
        }

        if (expectedType != null && !inputType.contains(expectedType)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The id suggests this field should be a " + expectedType +
                    ", but the inputType does not include it");
        }
    }
}