package com.android.tools.lint.checks;

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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    private static final String TAG_EDIT_TEXT = "EditText";
    private static final String TAG_AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String TAG_MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";
    private static final String TAG_EXTRACT_EDIT_TEXT = "ExtractEditText";

    private static final String ATTR_INPUT_TYPE = "android:inputType";
    private static final String ATTR_ID = "android:id";

    private static final String[] EMAIL_IDS = {"email", "e-mail", "mail"};
    private static final String[] PHONE_IDS = {"phone", "telephone", "tel", "fax"};
    private static final String[] URL_IDS = {"url", "uri", "link"};
    private static final String[] PASSWORD_IDS = {"password", "pwd", "passwd"};

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `android:inputType` attribute on a text field improves "
                            + "usability because optimized keyboards can be shown to the user "
                            + "(such as just digits and parentheses for a phone number). This "
                            + "check also looks at the field's `android:id`; when the id contains "
                            + "hints such as `phone` or `email`, it ensures that `inputType` "
                            + "includes the corresponding type. If you really want a generic "
                            + "plain-text field, you can suppress this warning by setting "
                            + "`inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_EDIT_TEXT,
                TAG_AUTO_COMPLETE_TEXT_VIEW,
                TAG_MULTI_AUTO_COMPLETE_TEXT_VIEW,
                TAG_EXTRACT_EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttribute(ATTR_INPUT_TYPE);
        String id = element.getAttribute(ATTR_ID);

        if (inputType.isEmpty()) {
            String[] expected = getExpectedInputTypes(id);
            String message;
            if (expected != null) {
                message = String.format(
                        "This text field is missing an `android:inputType`; expected `%1$s` for this %2$s field",
                        expected[0],
                        getDisplayName(expected[0]));
            } else {
                message = "This text field is missing an `android:inputType`";
            }
            context.report(ISSUE, element, context.getElementLocation(element), message);
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        String[] expected = getExpectedInputTypes(id);
        if (expected != null) {
            for (String expectedType : expected) {
                if (inputTypeContains(inputType, expectedType)) {
                    return;
                }
            }

            Attr attr = element.getAttributeNode(ATTR_INPUT_TYPE);
            Location location = attr != null
                    ? context.getValueLocation(attr)
                    : context.getElementLocation(element);
            context.report(
                    ISSUE,
                    element,
                    location,
                    String.format(
                            "The id `%1$s` suggests this is a %2$s field, but `android:inputType` does not include `%3$s`",
                            id,
                            getDisplayName(expected[0]),
                            expected[0]));
        }
    }

    private static String[] getExpectedInputTypes(String id) {
        String name = getLocalName(id);
        if (name == null) {
            return null;
        }

        if (containsAny(name, EMAIL_IDS)) {
            return new String[]{"textEmailAddress", "textWebEmailAddress"};
        }
        if (containsAny(name, PHONE_IDS)) {
            return new String[]{"phone"};
        }
        if (containsAny(name, URL_IDS)) {
            return new String[]{"textUri"};
        }
        if (containsAny(name, PASSWORD_IDS)) {
            return new String[]{"textPassword", "textVisiblePassword", "numberPassword"};
        }

        return null;
    }

    private static String getLocalName(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        String name = id;
        int slash = id.lastIndexOf('/');
        if (slash != -1) {
            name = id.substring(slash + 1);
        }

        if (name.startsWith("+")) {
            name = name.substring(1);
        }
        if (name.startsWith("@")) {
            name = name.substring(1);
        }

        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String name, String... keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inputTypeContains(String inputType, String expectedType) {
        for (String token : inputType.split("\\|")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()
                    && trimmed.equalsIgnoreCase(expectedType)) {
                return true;
            }
        }
        return false;
    }

    private static String getDisplayName(String expectedType) {
        String lower = expectedType.toLowerCase(Locale.ROOT);
        if (lower.contains("email")) {
            return "email";
        }
        if (lower.contains("phone")) {
            return "phone";
        }
        if (lower.contains("uri") || lower.contains("url")) {
            return "URL";
        }
        if (lower.contains("password")) {
            return "password";
        }
        return expectedType;
    }
}