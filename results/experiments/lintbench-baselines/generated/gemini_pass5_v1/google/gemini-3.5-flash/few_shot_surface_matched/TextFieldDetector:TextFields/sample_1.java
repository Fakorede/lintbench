package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_ID = "id";

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType or useful inputType",
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because depending on the data to be input, optimized keyboards "
                            + "can be shown to the user (such as just digits and parentheses "
                            + "for a phone number).\n\n"
                            + "The lint detector also looks at the `id` of the view, and if "
                            + "the id offers a hint of the purpose of the field (for example, "
                            + "the `id` contains the phrase `phone` or `email`), then lint "
                            + "will also ensure that the `inputType` contains the corresponding "
                            + "type attributes.\n\n"
                            + "If you really want to keep the text field generic, you can "
                            + "suppress this warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView",
                "android.widget.EditText",
                "android.widget.AutoCompleteTextView",
                "android.widget.MultiAutoCompleteTextView"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputType == null || inputType.isEmpty()) {
            if (element.hasAttributeNS(ANDROID_URI, "password")
                    || element.hasAttributeNS(ANDROID_URI, "numeric")
                    || element.hasAttributeNS(ANDROID_URI, "phoneNumber")
                    || element.hasAttributeNS(ANDROID_URI, "inputMethod")) {
                return;
            }

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `inputType` attribute"
            );
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String idName = id;
        int slash = id.lastIndexOf('/');
        if (slash != -1) {
            idName = id.substring(slash + 1);
        }
        String lowerId = idName.toLowerCase(Locale.US);

        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputTypeAttr == null) {
            return;
        }

        if (lowerId.contains("phone") || lowerId.contains("tele")) {
            if (!inputType.contains("phone")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "An `inputType` of `phone` should be used when the ID suggests a phone number"
                );
            }
        } else if (lowerId.contains("email")) {
            if (!inputType.contains("textEmailAddress") && !inputType.contains("textWebEmailAddress")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "An `inputType` of `textEmailAddress` should be used when the ID suggests an email address"
                );
            }
        } else if (lowerId.contains("password")) {
            if (!inputType.contains("textPassword") && !inputType.contains("numberPassword")
                    && !inputType.contains("textVisiblePassword") && !inputType.contains("textWebPassword")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "A password `inputType` should be used when the ID suggests a password"
                );
            }
        } else if (lowerId.contains("postal") || lowerId.contains("zip")) {
            if (!inputType.contains("textPostalAddress")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "An `inputType` of `textPostalAddress` should be used when the ID suggests a postal address"
                );
            }
        } else if (lowerId.contains("number") || lowerId.contains("numeric")) {
            if (!inputType.contains("number") && !inputType.contains("phone")
                    && !inputType.contains("date") && !inputType.contains("time") && !inputType.contains("datetime")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "A numeric `inputType` should be used when the ID suggests a number"
                );
            }
        }
    }
}