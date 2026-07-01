package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_ID = "id";
    private static final String EDIT_TEXT = "EditText";

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
        return Collections.singletonList(EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputTypeNode == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "EditText is missing an `inputType` attribute");
            return;
        }

        String inputType = inputTypeNode.getValue();
        Attr idNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idNode != null) {
            String id = idNode.getValue();
            String idName = id.substring(id.lastIndexOf('/') + 1);
            String suggestedType = null;
            String inputTypeLower = inputType.toLowerCase(Locale.US);

            if (idName.contains("phone") || idName.contains("tele")) {
                if (!inputTypeLower.contains("phone")) {
                    suggestedType = "phone";
                }
            } else if (idName.contains("email")) {
                if (!inputTypeLower.contains("email")) {
                    suggestedType = "textEmailAddress";
                }
            } else if (idName.contains("password") || idName.contains("pass")) {
                if (!inputTypeLower.contains("password")) {
                    suggestedType = "textPassword";
                }
            } else if (idName.contains("uri") || idName.contains("url") || idName.contains("website")) {
                if (!inputTypeLower.contains("uri")) {
                    suggestedType = "textUri";
                }
            } else if (idName.contains("postal") || idName.contains("zip")) {
                if (!inputTypeLower.contains("postal")) {
                    suggestedType = "textPostalAddress";
                }
            } else if (idName.contains("numeric") || idName.contains("number") || idName.contains("amount")) {
                if (!inputTypeLower.contains("number") && !inputTypeLower.contains("phone")
                        && !inputTypeLower.contains("date") && !inputTypeLower.contains("time")) {
                    suggestedType = "number";
                }
            } else if (idName.contains("date")) {
                if (!inputTypeLower.contains("date")) {
                    suggestedType = "date";
                }
            } else if (idName.contains("time")) {
                if (!inputTypeLower.contains("time")) {
                    suggestedType = "time";
                }
            }

            if (suggestedType != null) {
                String message = String.format(
                        "Based on the ID of the `EditText` (`%1$s`), the `inputType` should probably be `%2$s`",
                        idName, suggestedType);
                context.report(ISSUE, inputTypeNode, context.getLocation(inputTypeNode), message);
            }
        }
    }
}