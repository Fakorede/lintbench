package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number).\n"
                            + "\n"
                            + "The lint detector also looks at the `id` of the view, and if the id offers a "
                            + "hint of the purpose of the field (for example, the `id` contains the phrase "
                            + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                            + "the corresponding type attributes.\n"
                            + "\n"
                            + "If you really want to keep the text field generic, you can suppress this warning "
                            + "by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_PASSWORD = "password";
    private static final String ATTR_PHONE_NUMBER = "phoneNumber";
    private static final String ATTR_ID = "id";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "android.widget.EditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_INPUT_TYPE);

        if (inputTypeAttr == null) {
            // Also check for the old password/phoneNumber attributes
            Attr passwordAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_PASSWORD);
            Attr phoneNumberAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_PHONE_NUMBER);

            if (passwordAttr != null || phoneNumberAttr != null) {
                // Old-style attributes are present; no warning needed for missing inputType
                return;
            }

            Attr idAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_ID);
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This text field does not specify an `inputType` or a `hint`");
            return;
        }

        String inputType = inputTypeAttr.getValue();

        // Check if the id hints at a specific type and verify the inputType matches
        Attr idAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue().toLowerCase();
            // Strip @+id/ or @id/ prefix
            int slashIndex = id.lastIndexOf('/');
            if (slashIndex >= 0) {
                id = id.substring(slashIndex + 1);
            }

            String inputTypeLower = inputType.toLowerCase();

            if (containsWord(id, "phone")) {
                if (!inputTypeLower.contains("phone")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getLocation(inputTypeAttr),
                            "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a phone "
                                    + "number, but the `inputType` does not include `phone`");
                }
            } else if (containsWord(id, "email")) {
                if (!inputTypeLower.contains("email")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getLocation(inputTypeAttr),
                            "The view's `id` (`" + idAttr.getValue() + "`) suggests this is an e-mail "
                                    + "address, but the `inputType` does not include `textEmailAddress`");
                }
            } else if (containsWord(id, "password") || containsWord(id, "passwd")
                    || containsWord(id, "pwd")) {
                if (!inputTypeLower.contains("password")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getLocation(inputTypeAttr),
                            "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a password "
                                    + "field, but the `inputType` does not include `textPassword`");
                }
            } else if (containsWord(id, "uri") || containsWord(id, "url")
                    || containsWord(id, "link") || containsWord(id, "website")) {
                if (!inputTypeLower.contains("texturl") && !inputTypeLower.contains("textweburi")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getLocation(inputTypeAttr),
                            "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a URI, "
                                    + "but the `inputType` does not include `textUri`");
                }
            } else if (containsWord(id, "postal") || containsWord(id, "zip")) {
                if (!inputTypeLower.contains("textpostaladdress") && !inputTypeLower.contains("number")) {
                    context.report(
                            ISSUE,
                            inputTypeAttr,
                            context.getLocation(inputTypeAttr),
                            "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a postal "
                                    + "address, but the `inputType` does not include `textPostalAddress`");
                }
            }
        }
    }

    /**
     * Returns true if the given string contains the given word, where word boundaries
     * are defined by underscores, camelCase transitions, or string boundaries.
     */
    private static boolean containsWord(@NonNull String name, @NonNull String word) {
        // Check for exact match
        if (name.equals(word)) {
            return true;
        }

        int index = name.indexOf(word);
        while (index >= 0) {
            // Check character before
            boolean startOk = (index == 0)
                    || !Character.isLetterOrDigit(name.charAt(index - 1))
                    || Character.isUpperCase(word.charAt(0));
            // Check character after
            int end = index + word.length();
            boolean endOk = (end == name.length())
                    || !Character.isLetterOrDigit(name.charAt(end))
                    || Character.isUpperCase(name.charAt(end));

            if (startOk && endOk) {
                return true;
            }

            index = name.indexOf(word, index + 1);
        }

        return false;
    }
}