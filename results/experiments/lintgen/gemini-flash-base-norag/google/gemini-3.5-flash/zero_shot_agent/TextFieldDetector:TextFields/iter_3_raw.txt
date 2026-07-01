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
        return ALL;
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
        int slash = idValue.indexOf('/');
        String id = (slash >= 0 ? idValue.substring(slash + 1) : idValue).toLowerCase(Locale.US);
        String inputType = inputTypeValue.toLowerCase(Locale.US);

        if (containsWord(id, "phone", true, false)) {
            if (!inputType.contains("phone")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for phone numbers, but its `inputType` is not `phone`"
                );
            }
        } else if (containsWord(id, "email", true, false)) {
            if (!inputType.contains("email")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for email addresses, but its `inputType` is not `textEmailAddress`"
                );
            }
        } else if (containsWord(id, "password", true, false)) {
            if (!inputType.contains("password")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for passwords, but its `inputType` does not specify a password type"
                );
            }
        } else if (containsWord(id, "url", true, false) || containsWord(id, "uri", true, false)) {
            if (!inputType.contains("uri")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for URLs, but its `inputType` is not `textUri`"
                );
            }
        } else if (containsWord(id, "postal", true, false) || containsWord(id, "zip", true, false)) {
            if (!inputType.contains("postal") && !inputType.contains("number")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for postal codes, but its `inputType` is not `textPostalAddress` or `number`"
                );
            }
        } else if (containsWord(id, "numeric", true, false) || containsWord(id, "number", true, false)) {
            if (!inputType.contains("number")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for numbers, but its `inputType` is not `number`"
                );
            }
        } else if (containsWord(id, "date", true, false)) {
            if (!inputType.contains("date") && !inputType.contains("datetime")) {
                context.report(
                        ISSUE,
                        inputTypeAttr,
                        context.getLocation(inputTypeAttr),
                        "This text field has an input id indicating it is for dates, but its `inputType` is not `date` or `datetime`"
                );
            }
        } else if (containsWord(id, "time", true, false)) {
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

    public static boolean containsWord(String name, String word) {
        return containsWord(name, word, false, false);
    }

    public static boolean containsWord(String name, String word, boolean allowPrefix,
            boolean allowSuffix) {
        int index = 0;
        int wordLength = word.length();
        int nameLength = name.length();
        while (true) {
            index = name.indexOf(word, index);
            if (index == -1) {
                return false;
            }

            if (index > 0) {
                char prev = name.charAt(index - 1);
                if (Character.isLetter(prev) && !allowPrefix) {
                    index++;
                    continue;
                }
            }

            int end = index + wordLength;
            if (end < nameLength) {
                char next = name.charAt(end);
                if (Character.isLetter(next) && !allowSuffix) {
                    index++;
                    continue;
                }
            }

            return true;
        }
    }
}