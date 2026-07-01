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
import java.util.Arrays;
import java.util.Collection;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";
    private static final String ATTR_INPUT_TYPE = "inputType";

    private static final String[] TEXT_FIELDS = {
            "EditText",
            "ExtractEditText",
            "AutoCompleteTextView",
            "MultiAutoCompleteTextView",
            "TextInputEditText"
    };

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType",
                    "Providing an inputType attribute on a text field improves usability because "
                            + "depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number)."
                            + "\n\nThis check also looks at the id of the view, and if the id "
                            + "offers a hint of the purpose of the field (for example, the id "
                            + "contains the phrase phone or email), then lint will also ensure that "
                            + "the inputType contains the corresponding type attributes.\n\n"
                            + "If you really want to keep the text field generic, you can suppress "
                            + "this warning by setting inputType=\"text\".",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TEXT_FIELDS);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);

        if ("text".equals(inputType)) {
            return;
        }

        if (inputType == null || inputType.isEmpty()) {
            if (idContains(id, "email")) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "This text field's id suggests it should be an email field, but it does not specify an inputType");
            } else if (idContains(id, "phone")) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "This text field's id suggests it should be a phone field, but it does not specify an inputType");
            } else {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "This text field should specify an inputType");
            }
            return;
        }

        String[] types = inputType.split("\\|");
        if (idContains(id, "email") && !containsType(types, "email")) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This text field's id suggests it should be an email field, but the inputType does not include an email type");
        } else if (idContains(id, "phone") && !containsType(types, "phone")) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This text field's id suggests it should be a phone field, but the inputType does not include a phone type");
        }
    }

    private static boolean idContains(String id, String hint) {
        return id != null && id.toLowerCase().contains(hint);
    }

    private static boolean containsType(String[] types, String type) {
        for (String t : types) {
            if (t.toLowerCase().contains(type)) {
                return true;
            }
        }
        return false;
    }
}