package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.EXTRACT_EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

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
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability because"
                            + " depending on the data to be input, optimized keyboards can be shown"
                            + " to the user (such as just digits and parentheses for a phone"
                            + " number).\\n\\nThe lint detector also looks at the `id` of the view,"
                            + " and if the `id` offers a hint of the purpose of the field (for"
                            + " example, the `id` contains the phrase `phone` or `email`), then lint"
                            + " will also ensure that the `inputType` contains the corresponding"
                            + " type attributes.\\n\\nIf you really want to keep the text field"
                            + " generic, you can suppress this warning by setting"
                            + " `inputType=\\\"text\\\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT, AUTO_COMPLETE_TEXT_VIEW, MULTI_AUTO_COMPLETE_TEXT_VIEW, EXTRACT_EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputTypeNode == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This text field does not specify an inputType");
            return;
        }

        String inputType = inputTypeNode.getValue();
        if ("text".equals(inputType)) {
            return;
        }

        String id = getLocalId(element);
        if (id == null) {
            return;
        }

        String lowerId = id.toLowerCase(Locale.US);
        String lowerInputType = inputType.toLowerCase(Locale.US);

        if ((lowerId.contains("phone") || lowerId.contains("tel"))
                && !lowerInputType.contains("phone")) {
            reportTypeMismatch(context, inputTypeNode, "phone");
        } else if ((lowerId.contains("email") || lowerId.contains("e-mail"))
                && !lowerInputType.contains("email")) {
            reportTypeMismatch(context, inputTypeNode, "email");
        } else if ((lowerId.contains("password") || lowerId.contains("passwd"))
                && !lowerInputType.contains("password")) {
            reportTypeMismatch(context, inputTypeNode, "password");
        } else if (lowerId.contains("name") && !lowerInputType.contains("personname")) {
            reportTypeMismatch(context, inputTypeNode, "personName");
        } else if ((lowerId.contains("address") || lowerId.contains("addr"))
                && !lowerInputType.contains("postaladdress")) {
            reportTypeMismatch(context, inputTypeNode, "postalAddress");
        } else if ((lowerId.contains("url") || lowerId.contains("uri"))
                && !lowerInputType.contains("uri")) {
            reportTypeMismatch(context, inputTypeNode, "uri");
        } else if ((lowerId.contains("number") || lowerId.contains("amount"))
                && !lowerInputType.contains("number")) {
            reportTypeMismatch(context, inputTypeNode, "number");
        }
    }

    private static String getLocalId(Element element) {
        Attr idNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idNode == null) {
            return null;
        }
        String id = idNode.getValue();
        int slash = id.lastIndexOf('/');
        if (slash != -1 && slash < id.length() - 1) {
            id = id.substring(slash + 1);
        }
        return id;
    }

    private static void reportTypeMismatch(
            @NonNull XmlContext context, @NonNull Attr inputTypeNode, @NonNull String expected) {
        context.report(
                ISSUE,
                inputTypeNode,
                context.getLocation(inputTypeNode),
                "The id of this field suggests it is for "
                        + expected
                        + " data, but the inputType does not contain the corresponding type");
    }
}