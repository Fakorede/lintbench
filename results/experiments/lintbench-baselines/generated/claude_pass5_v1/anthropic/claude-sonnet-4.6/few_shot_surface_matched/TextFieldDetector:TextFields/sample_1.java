package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.EDIT_TEXT;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

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
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        // Also check for inputMethod (older attribute, deprecated but still used)
        if (inputType == null || inputType.isEmpty()) {
            // Check if there's a phone attribute or similar that might indicate purpose
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            if (id == null) {
                id = "";
            }
            // Strip the id prefix (@+id/, @id/)
            String idLower = id.toLowerCase();
            int slashIndex = idLower.lastIndexOf('/');
            if (slashIndex >= 0) {
                idLower = idLower.substring(slashIndex + 1);
            }

            String message = "This text field does not specify an `inputType` or a `hint`";
            context.report(ISSUE, element, context.getNameLocation(element), message);
        } else {
            // inputType is set; check if the id suggests a specific type that should be reflected
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            if (id == null) {
                id = "";
            }
            String idLower = id.toLowerCase();
            int slashIndex = idLower.lastIndexOf('/');
            if (slashIndex >= 0) {
                idLower = idLower.substring(slashIndex + 1);
            }

            String inputTypeLower = inputType.toLowerCase();

            if (idContains(idLower, "phone")) {
                if (!inputTypeLower.contains("phone")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "The view's `id` (`" + id + "`) suggests this is a phone number, "
                                    + "but the `inputType` does not include `phone`");
                }
            } else if (idContains(idLower, "email")) {
                if (!inputTypeLower.contains("textEmailAddress")) {
                    if (!inputType.contains("textEmailAddress")) {
                        context.report(
                                ISSUE,
                                element,
                                context.getNameLocation(element),
                                "The view's `id` (`" + id + "`) suggests this is an e-mail address, "
                                        + "but the `inputType` does not include `textEmailAddress`");
                    }
                }
            } else if (idContains(idLower, "password") || idContains(idLower, "passwd")) {
                if (!inputTypeLower.contains("textpassword") && !inputTypeLower.contains("numberpassword")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "The view's `id` (`" + id + "`) suggests this is a password field, "
                                    + "but the `inputType` does not include `textPassword`");
                }
            } else if (idContains(idLower, "uri") || idContains(idLower, "url") || idContains(idLower, "website") || idContains(idLower, "web")) {
                if (!inputTypeLower.contains("textUri")) {
                    if (!inputType.contains("textUri")) {
                        context.report(
                                ISSUE,
                                element,
                                context.getNameLocation(element),
                                "The view's `id` (`" + id + "`) suggests this is a URI, "
                                        + "but the `inputType` does not include `textUri`");
                    }
                }
            }
        }
    }

    private static boolean idContains(@NonNull String id, @NonNull String keyword) {
        return id.contains(keyword.toLowerCase());
    }
}