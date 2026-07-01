package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

    public static final Issue ISSUE = Issue.create(
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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(SdkConstants.ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (!tagName.equals(SdkConstants.EDIT_TEXT)
                && !tagName.equals(SdkConstants.AUTO_COMPLETE_TEXT_VIEW)
                && !tagName.equals(SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW)
                && !tagName.endsWith("EditText")) {
            return;
        }

        Attr inputTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (inputTypeAttr == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `inputType` attribute"
            );
            return;
        }

        String inputTypeValue = inputTypeAttr.getValue();
        if ("text".equals(inputTypeValue)) {
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String idValue = idAttr.getValue();
        String id = idValue.substring(idValue.indexOf('/') + 1).toLowerCase(Locale.US);
        String inputType = inputTypeValue.toLowerCase(Locale.US);

        if (id.contains("phone")) {
            if (!inputType.contains("phone")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for phone numbers, but its `inputType` is not `phone`"
                );
            }
        } else if (id.contains("email")) {
            if (!inputType.contains("email")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for email addresses, but its `inputType` is not `textEmailAddress`"
                );
            }
        } else if (id.contains("password")) {
            if (!inputType.contains("password")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for passwords, but its `inputType` does not specify a password type"
                );
            }
        } else if (id.contains("url") || id.contains("uri")) {
            if (!inputType.contains("uri")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for URLs, but its `inputType` is not `textUri`"
                );
            }
        } else if (id.contains("postal") || id.contains("zip")) {
            if (!inputType.contains("postal") && !inputType.contains("number")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for postal codes, but its `inputType` is not `textPostalAddress` or `number`"
                );
            }
        } else if (id.contains("numeric") || id.contains("number")) {
            if (!inputType.contains("number")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for numbers, but its `inputType` is not `number`"
                );
            }
        } else if (id.contains("date")) {
            if (!inputType.contains("date") && !inputType.contains("datetime")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for dates, but its `inputType` is not `date` or `datetime`"
                );
            }
        } else if (id.contains("time")) {
            if (!inputType.contains("time") && !inputType.contains("datetime")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for times, but its `inputType` is not `time` or `datetime`"
                );
            }
        }
    }
}