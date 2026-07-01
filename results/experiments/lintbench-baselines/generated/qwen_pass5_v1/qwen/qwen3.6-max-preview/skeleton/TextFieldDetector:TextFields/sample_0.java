package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
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
                    "to the user (such as just digits and parentheses for a phone number). " +
                    "The lint detector also looks at the `id` of the view, and if the id offers a " +
                    "hint of the purpose of the field (for example, the `id` contains the phrase " +
                    "`phone` or `email`), then lint will also ensure that the `inputType` contains " +
                    "the corresponding type attributes. If you really want to keep the text field " +
                    "generic, you can suppress this warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("EditText", "AutoCompleteTextView", "TextInputEditText");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String ns = "http://schemas.android.com/apk/res/android";
        Attr inputTypeAttr = element.getAttributeNodeNS(ns, "inputType");
        String inputType = inputTypeAttr != null ? inputTypeAttr.getValue() : null;

        if (inputType == null || inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `inputType` attribute. Providing an `inputType` improves usability " +
                    "because depending on the data to be input, optimized keyboards can be shown " +
                    "to the user. If you really want to keep the text field generic, you can suppress " +
                    "this warning by setting `inputType=\"text\"`.");
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(ns, "id");
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue();
        int slashIndex = id.lastIndexOf('/');
        String idName = slashIndex != -1 ? id.substring(slashIndex + 1) : id;
        String lowerId = idName.toLowerCase();

        String expectedType = null;
        if (lowerId.contains("phone")) {
            expectedType = "phone";
        } else if (lowerId.contains("email")) {
            expectedType = "textEmailAddress";
        } else if (lowerId.contains("password")) {
            expectedType = "textPassword";
        } else if (lowerId.contains("number") || lowerId.contains("amount") || lowerId.contains("price")) {
            expectedType = "number";
        } else if (lowerId.contains("url") || lowerId.contains("link") || lowerId.contains("web")) {
            expectedType = "textUri";
        } else if (lowerId.contains("date")) {
            expectedType = "date";
        } else if (lowerId.contains("time")) {
            expectedType = "time";
        }

        if (expectedType != null && !inputType.contains(expectedType)) {
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                    "The id `" + idName + "` suggests this field is for " + expectedType +
                    ", but the `inputType` is `" + inputType + "`. Consider using `android:inputType=\"" + expectedType + "\"`.");
        }
    }
}