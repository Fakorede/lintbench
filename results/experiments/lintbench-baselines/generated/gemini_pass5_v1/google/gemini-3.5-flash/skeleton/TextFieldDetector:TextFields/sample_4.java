package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
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

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("EditText");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS("http://schemas.android.com/apk/res/android", "inputType");
        if (inputType.isEmpty()) {
            String editable = element.getAttributeNS("http://schemas.android.com/apk/res/android", "editable");
            if ("false".equals(editable)) {
                return;
            }
            context.report(ISSUE, element, context.getLocation(element), "Missing `inputType` attribute");
            return;
        }

        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id.isEmpty()) {
            return;
        }

        String idName = id;
        int index = id.lastIndexOf('/');
        if (index != -1) {
            idName = id.substring(index + 1);
        }
        idName = idName.toLowerCase();

        if (idName.contains("phone") || idName.contains("tel")) {
            if (!inputType.contains("phone")) {
                context.report(ISSUE, element, context.getLocation(element), 
                    String.format("Input type should be `phone` (or similar) since the id contains '%s'", idName.contains("phone") ? "phone" : "tel"));
            }
        } else if (idName.contains("email")) {
            if (!inputType.contains("textEmailAddress") && !inputType.contains("textWebEmailAddress")) {
                context.report(ISSUE, element, context.getLocation(element), 
                    "Input type should be `textEmailAddress` since the id contains 'email'");
            }
        } else if (idName.contains("password") || idName.contains("passwd")) {
            if (!inputType.contains("textPassword") && !inputType.contains("textVisiblePassword") && 
                !inputType.contains("numberPassword") && !inputType.contains("textWebPassword")) {
                context.report(ISSUE, element, context.getLocation(element), 
                    String.format("Input type should be `textPassword` (or similar) since the id contains '%s'", idName.contains("password") ? "password" : "passwd"));
            }
        } else if (idName.contains("postal") || idName.contains("zip")) {
            if (!inputType.contains("postalAddress") && !inputType.contains("number")) {
                context.report(ISSUE, element, context.getLocation(element), 
                    String.format("Input type should be `postalAddress` (or similar) since the id contains '%s'", idName.contains("postal") ? "postal" : "zip"));
            }
        } else if (idName.contains("numeric") || idName.contains("number")) {
            if (!inputType.contains("number") && !inputType.contains("phone") && 
                !inputType.contains("datetime") && !inputType.contains("date") && 
                !inputType.contains("time") && !inputType.contains("decimal")) {
                context.report(ISSUE, element, context.getLocation(element), 
                    String.format("Input type should be `number` (or similar) since the id contains '%s'", idName.contains("numeric") ? "numeric" : "number"));
            }
        } else if (idName.contains("date")) {
            if (!inputType.contains("date") && !inputType.contains("datetime") && !inputType.contains("time")) {
                context.report(ISSUE, element, context.getLocation(element), 
                    "Input type should be `date` (or similar) since the id contains 'date'");
            }
        } else if (idName.contains("time")) {
            if (!inputType.contains("time") && !inputType.contains("datetime") && !inputType.contains("date")) {
                context.report(ISSUE, element, context.getLocation(element), 
                    "Input type should be `time` (or similar) since the id contains 'time'");
            }
        }
    }
}