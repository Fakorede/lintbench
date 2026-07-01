package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Locale;

public class TextFieldDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability " +
            "because depending on the data to be input, optimized keyboards can be shown " +
            "to the user (such as just digits and parentheses for a phone number).\n\n" +
            "The lint detector also looks at the `id` of the view, and if the id offers a " +
            "hint of the purpose of the field (for example, the `id` contains the phrase " +
            "`phone` or `email`), then lint will also ensure that the `inputType` contains " +
            "the corresponding type attributes.\n\n" +
            "If you really want to keep the text field generic, you can suppress this warning " +
            "by setting `inputType=\"text\"`.",
            Category.USABILITY, 5, Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (!tag.equals(SdkConstants.EDIT_TEXT) && !tag.endsWith(".EditText")) {
            return;
        }

        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        if (inputType.equals("text")) {
            return;
        }

        if (inputType.startsWith("@")) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (!id.isEmpty()) {
            String idName = getResourceName(id);
            if (idName != null) {
                String lower = idName.toLowerCase(Locale.US);
                if (!matchesHint(inputType, lower)) {
                    String expected = getExpectedType(lower);
                    context.report(ISSUE, element, context.getLocation(element),
                            "The id `" + idName + "` suggests this field is for " + expected +
                            ", but the inputType is `" + inputType + "`");
                }
            }
        }
    }

    private static String getResourceName(String id) {
        if (id.startsWith("@+id/") || id.startsWith("@id/")) {
            return id.substring(id.indexOf('/') + 1);
        }
        return null;
    }

    private static boolean inputTypeContains(String inputType, String type) {
        if (inputType.equals(type)) {
            return true;
        }
        String[] parts = inputType.split("\\|");
        for (String part : parts) {
            if (part.trim().equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesHint(String inputType, String lower) {
        if (lower.contains("phone")) {
            return inputTypeContains(inputType, "phone");
        }
        if (lower.contains("email")) {
            return inputTypeContains(inputType, "textEmailAddress");
        }
        if (lower.contains("password")) {
            return inputTypeContains(inputType, "textPassword") || inputTypeContains(inputType, "numberPassword");
        }
        if (lower.contains("date")) {
            return inputTypeContains(inputType, "date");
        }
        if (lower.contains("time")) {
            return inputTypeContains(inputType, "time");
        }
        if (lower.contains("number") || lower.contains("amount") || lower.contains("price")) {
            return inputTypeContains(inputType, "number") || inputTypeContains(inputType, "numberDecimal") || inputTypeContains(inputType, "numberSigned");
        }
        if (lower.contains("web") || lower.contains("url") || lower.contains("uri")) {
            return inputTypeContains(inputType, "textUri");
        }
        if (lower.contains("postal") || lower.contains("zip")) {
            return inputTypeContains(inputType, "textPostalAddress");
        }
        if (lower.contains("person") || lower.contains("name")) {
            return inputTypeContains(inputType, "textPersonName");
        }
        return true;
    }

    private static String getExpectedType(String lower) {
        if (lower.contains("phone")) return "phone numbers";
        if (lower.contains("email")) return "email addresses";
        if (lower.contains("password")) return "passwords";
        if (lower.contains("date")) return "dates";
        if (lower.contains("time")) return "times";
        if (lower.contains("number") || lower.contains("amount") || lower.contains("price")) return "numbers";
        if (lower.contains("web") || lower.contains("url") || lower.contains("uri")) return "URIs";
        if (lower.contains("postal") || lower.contains("zip")) return "postal addresses";
        if (lower.contains("person") || lower.contains("name")) return "names";
        return "specific input";
    }
}