package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;

public class TextFieldDetector extends LayoutDetector {

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
        return Arrays.asList(SdkConstants.EDIT_TEXT, SdkConstants.AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        boolean hasInputType = inputType != null && !inputType.isEmpty();

        if (!hasInputType) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        // If it's a resource reference, we can't statically verify hints
        if (inputType.startsWith("@") || inputType.startsWith("?")) {
            return;
        }

        // Explicitly generic text field suppresses further hint checks
        if (inputType.trim().equals("text")) {
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        // Extract id name from @+id/name or @id/name
        String idName = id;
        int slash = idName.lastIndexOf('/');
        if (slash != -1) {
            idName = idName.substring(slash + 1);
        }
        String lowerId = idName.toLowerCase(Locale.US);

        String expectedType = null;
        String hintDescription = null;

        if (lowerId.contains("phone")) {
            expectedType = "phone";
            hintDescription = "a phone number";
        } else if (lowerId.contains("email")) {
            expectedType = "textEmailAddress";
            hintDescription = "an email address";
        } else if (lowerId.contains("password")) {
            expectedType = "textPassword";
            hintDescription = "a password";
        } else if (lowerId.contains("number")) {
            expectedType = "number";
            hintDescription = "a number";
        } else if (lowerId.contains("date")) {
            expectedType = "date";
            hintDescription = "a date";
        } else if (lowerId.contains("time")) {
            expectedType = "time";
            hintDescription = "a time";
        } else if (lowerId.contains("url") || lowerId.contains("uri") || lowerId.contains("web")) {
            expectedType = "textUri";
            hintDescription = "a URL";
        } else if (lowerId.contains("postal") || lowerId.contains("address")) {
            expectedType = "textPostalAddress";
            hintDescription = "a postal address";
        }

        if (expectedType != null) {
            String[] types = inputType.split("\\|");
            boolean found = false;
            for (String type : types) {
                String trimmed = type.trim();
                if (trimmed.equals(expectedType)) {
                    found = true;
                    break;
                }
                // Allow related/compatible types
                if (expectedType.equals("textPassword") && (trimmed.equals("numberPassword") || trimmed.equals("textVisiblePassword"))) {
                    found = true;
                    break;
                }
                if (expectedType.equals("number") && trimmed.equals("phone")) {
                    found = true;
                    break;
                }
                if (expectedType.equals("phone") && trimmed.equals("number")) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                String message = String.format(
                        "The id `%1$s` suggests this field is for %2$s, but the inputType is \"%3$s\". " +
                        "Consider adding `android:inputType=\"%4$s\"`.",
                        idName, hintDescription, inputType, expectedType);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }
}