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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.ATTR_PASSWORD;
import static com.android.SdkConstants.EDIT_TEXT;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

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
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";

    // inputType flag values
    private static final String INPUT_TYPE_PHONE = "phone";
    private static final String INPUT_TYPE_EMAIL = "textEmailAddress";
    private static final String INPUT_TYPE_URI = "textUri";
    private static final String INPUT_TYPE_PASSWORD = "textPassword";
    private static final String INPUT_TYPE_VISIBLE_PASSWORD = "textVisiblePassword";
    private static final String INPUT_TYPE_WEB_PASSWORD = "textWebPassword";
    private static final String INPUT_TYPE_POSTAL = "textPostalAddress";
    private static final String INPUT_TYPE_PERSON_NAME = "textPersonName";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(EDIT_TEXT, AUTO_COMPLETE_TEXT_VIEW, MULTI_AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        // Check for legacy password attributes
        Attr passwordAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_PASSWORD);

        if (inputTypeAttr != null) {
            // inputType is set; check if it matches the id hints
            String inputType = inputTypeAttr.getValue();

            // Get the id to check for hints
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            if (idAttr == null) {
                return;
            }
            String id = idAttr.getValue().toLowerCase();

            // Strip @id/ or @+id/ prefix
            int slashIndex = id.lastIndexOf('/');
            if (slashIndex != -1) {
                id = id.substring(slashIndex + 1);
            }

            // Check phone
            if (containsWord(id, "phone") && !inputType.contains(INPUT_TYPE_PHONE)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests it might be a phone number; consider using "
                                + "`inputType=\"phone\"` instead");
                return;
            }

            // Check email
            if ((containsWord(id, "email")) && !inputType.contains(INPUT_TYPE_EMAIL)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests it might be an e-mail address; consider using "
                                + "`inputType=\"textEmailAddress\"` instead");
                return;
            }

            // Check URI / URL
            if ((containsWord(id, "url") || containsWord(id, "uri") || containsWord(id, "link"))
                    && !inputType.contains(INPUT_TYPE_URI)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests it might be a URL; consider using "
                                + "`inputType=\"textUri\"` instead");
                return;
            }

            // Check password
            if ((containsWord(id, "password") || containsWord(id, "passwd")
                    || containsWord(id, "pwd"))
                    && !inputType.contains(INPUT_TYPE_PASSWORD)
                    && !inputType.contains(INPUT_TYPE_VISIBLE_PASSWORD)
                    && !inputType.contains(INPUT_TYPE_WEB_PASSWORD)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests it might be a password; consider using "
                                + "`inputType=\"textPassword\"` instead");
                return;
            }

            // Check postal address
            if ((containsWord(id, "postal") || containsWord(id, "zip"))
                    && !inputType.contains(INPUT_TYPE_POSTAL)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests it might be a postal address; consider using "
                                + "`inputType=\"textPostalAddress\"` instead");
                return;
            }

            // Check person name
            if ((containsWord(id, "name") || containsWord(id, "firstname")
                    || containsWord(id, "lastname") || containsWord(id, "surname"))
                    && !inputType.contains(INPUT_TYPE_PERSON_NAME)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` suggests it might be a person's name; consider using "
                                + "`inputType=\"textPersonName\"` instead");
                return;
            }

            return;
        }

        // No inputType attribute set; report missing inputType
        // But first check if there's a legacy password attribute set (treated as a hint)
        String message = "This text field does not specify an `inputType` or a `hint`";

        Attr hintAttr = element.getAttributeNodeNS(ANDROID_URI, "hint");

        if (hintAttr != null && !hintAttr.getValue().isEmpty()) {
            // Has a hint; still missing inputType but message changes
            message = "This text field does not specify an `inputType`";
        }

        if (passwordAttr != null) {
            // Legacy password attribute is set; still missing inputType
            message = "This text field does not specify an `inputType`";
        }

        context.report(ISSUE, element, context.getLocation(element), message);
    }

    /**
     * Returns true if the given id string contains the given word (as a substring,
     * potentially separated by underscores, camel case boundaries, etc.).
     */
    private static boolean containsWord(@NonNull String id, @NonNull String word) {
        // Simple substring check (id is already lowercased)
        return id.contains(word.toLowerCase());
    }
}