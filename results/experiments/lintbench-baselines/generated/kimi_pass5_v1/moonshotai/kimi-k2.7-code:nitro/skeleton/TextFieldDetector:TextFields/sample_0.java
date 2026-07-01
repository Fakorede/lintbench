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
import java.util.Locale;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability because, "
                            + "depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number)."
                            + "\n\n"
                            + "This check also looks at the `id` of the view, and if the id offers a "
                            + "hint of the purpose of the field (for example, the id contains the "
                            + "phrase `phone` or `email`), it also ensures that the `inputType` "
                            + "contains the corresponding type attributes."
                            + "\n\n"
                            + "If you really want to keep the text field generic, you can suppress "
                            + "this warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView",
                "ExtractEditText",
                "android.support.v7.widget.AppCompatEditText",
                "android.support.v7.widget.AppCompatAutoCompleteTextView",
                "android.support.v7.widget.AppCompatMultiAutoCompleteTextView",
                "androidx.appcompat.widget.AppCompatEditText",
                "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
                "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
                "com.google.android.material.textfield.TextInputEditText",
                "com.google.android.material.textfield.MaterialAutoCompleteTextView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, "inputType");

        if (inputType == null || inputType.isEmpty()) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `inputType` attribute. Provide an `inputType` for this text field "
                            + "(use `inputType=\"text\"` for a generic text field).");
            return;
        }

        if ("text".equalsIgnoreCase(inputType.trim())) {
            return;
        }

        String idName = getIdName(element);
        if (idName == null) {
            return;
        }

        String lowerId = idName.toLowerCase(Locale.ROOT);
        String lowerInputType = inputType.toLowerCase(Locale.ROOT);

        if (lowerId.contains("phone") && !lowerInputType.contains("phone")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The id of this text field suggests it is a phone field, so the `inputType` "
                            + "should include a phone type.");
        }

        if (lowerId.contains("email") && !lowerInputType.contains("email")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The id of this text field suggests it is an email field, so the `inputType` "
                            + "should include an email type.");
        }
    }

    private static String getIdName(Element element) {
        String id = element.getAttributeNS(ANDROID_URI, "id");
        if (id == null || id.isEmpty()) {
            return null;
        }

        int slash = id.indexOf('/');
        if (slash != -1 && slash + 1 < id.length()) {
            return id.substring(slash + 1);
        }

        return id;
    }
}