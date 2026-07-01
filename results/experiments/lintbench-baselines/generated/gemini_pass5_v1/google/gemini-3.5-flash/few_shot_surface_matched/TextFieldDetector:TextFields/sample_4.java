package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType or useful inputType",
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
                    new Implementation(
                            TextFieldDetector.class,
                            Scope.LAYOUT_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                "EditText",
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView"
        );
    }

    @Override
    public void visitElement(
            @com.android.annotations.NonNull XmlContext context,
            @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String inputType = element.getAttributeNS("http://schemas.android.com/apk/res/android", "inputType");
        if (inputType == null || inputType.isEmpty()) {
            if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", "inputMethod")
                    || element.hasAttributeNS("http://schemas.android.com/apk/res/android", "numeric")
                    || element.hasAttributeNS("http://schemas.android.com/apk/res/android", "password")
                    || element.hasAttributeNS("http://schemas.android.com/apk/res/android", "phoneNumber")) {
                return;
            }
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `inputType` attribute");
        } else {
            String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
            if (id != null && !id.isEmpty()) {
                String idName = id.substring(id.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.US);
                org.w3c.dom.Attr inputTypeNode = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "inputType");
                com.android.tools.lint.detector.api.Location location = inputTypeNode != null
                        ? context.getValueLocation(inputTypeNode)
                        : context.getLocation(element);

                if (idName.contains("email") && !inputType.contains("textEmailAddress")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "inputType should be textEmailAddress");
                } else if (idName.contains("phone") && !inputType.contains("phone")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "inputType should be phone");
                } else if (idName.contains("password") && !inputType.contains("Password")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "inputType should be textPassword");
                } else if ((idName.contains("number") || idName.contains("numeric")) && !inputType.contains("number")) {
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "inputType should be number");
                }
            }
        }
    }
}