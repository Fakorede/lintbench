package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {
    private static final String TEXT = "text";
    private static final String ID = "id";

    private static final Implementation IMPLEMENTATION = new Implementation(
            TextFieldDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability because "
                    + "depending on the data to be input, optimized keyboards can be shown "
                    + "to the user (such as just digits and parentheses for a phone number). "
                    + "If you really want to keep the text field generic, you can suppress "
                    + "this warning by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (TEXT.equals(inputType)) {
            return;
        }

        String idValue = element.getAttributeNS(ANDROID_URI, ID);
        if (idValue == null || idValue.isEmpty()) {
            return;
        }

        String idName = stripIdPrefix(idValue);
        if (idName.isEmpty()) {
            return;
        }

        String lowerId = idName.toLowerCase(Locale.US);
        String lowerInputType = inputType.toLowerCase(Locale.US);

        if ((lowerId.contains("email") || lowerId.contains("e_mail"))
                && !lowerInputType.contains("email")) {
            context.report(ISSUE, element, context.getLocation(element),
                    String.format("The `id` `R.id.%s` suggests this is an email field, "
                            + "but the `inputType` does not include an email type", idName));
        }

        if ((lowerId.contains("phone") || lowerId.contains("tel"))
                && !lowerInputType.contains("phone")) {
            context.report(ISSUE, element, context.getLocation(element),
                    String.format("The `id` `R.id.%s` suggests this is a phone field, "
                            + "but the `inputType` is not `phone`", idName));
        }
    }

    private static String stripIdPrefix(String idValue) {
        int slash = idValue.lastIndexOf('/');
        if (slash != -1 && slash + 1 < idValue.length()) {
            return idValue.substring(slash + 1);
        }
        return idValue;
    }
}