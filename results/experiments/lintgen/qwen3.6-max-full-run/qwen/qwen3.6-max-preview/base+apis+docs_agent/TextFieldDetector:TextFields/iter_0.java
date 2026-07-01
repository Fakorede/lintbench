package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class TextFieldDetector extends Detector implements XmlScanner {

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
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "AutoCompleteTextView",
                "TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "inputType");
        if (inputTypeAttr != null) {
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "id");
        String id = idAttr != null ? idAttr.getValue() : "";
        String lowerId = id.toLowerCase();

        String suggestedType = null;
        if (lowerId.contains("phone")) {
            suggestedType = "phone";
        } else if (lowerId.contains("email")) {
            suggestedType = "textEmailAddress";
        } else if (lowerId.contains("password")) {
            suggestedType = "textPassword";
        } else if (lowerId.contains("number")) {
            suggestedType = "number";
        } else if (lowerId.contains("date")) {
            suggestedType = "date";
        } else if (lowerId.contains("time")) {
            suggestedType = "time";
        } else if (lowerId.contains("uri") || lowerId.contains("url")) {
            suggestedType = "textUri";
        }

        String message = "Missing `inputType` attribute";
        if (suggestedType != null) {
            message += String.format("; based on the ID, consider using `android:inputType=\"%s\"`", suggestedType);
        } else {
            message += "; if this field is generic, set `android:inputType=\"text\"` to suppress this warning.";
        }

        context.report(ISSUE, element, context.getLocation(element), message);
    }
}