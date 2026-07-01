package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class TextFieldDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability because " +
                    "depending on the data to be input, optimized keyboards can be shown to the " +
                    "user (such as just digits and parentheses for a phone number).\n" +
                    "\n" +
                    "This check also looks at the `id` of the view, and if the id offers a hint " +
                    "of the purpose of the field (for example, the id contains the phrase `phone` " +
                    "or `email`), then it will ensure that the `inputType` contains the " +
                    "corresponding type attributes.\n" +
                    "\n" +
                    "If you really want to keep the text field generic, you can suppress this " +
                    "warning by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final class Hint {
        final String keyword;
        final String display;
        final String expected;
        final String[] matches;

        Hint(String keyword, String display, String expected, String... matches) {
            this.keyword = keyword;
            this.display = display;
            this.expected = expected;
            this.matches = matches;
        }
    }

    private static final List<Hint> HINTS = Arrays.asList(
            new Hint("email", "email addresses", "textEmailAddress", "textemail"),
            new Hint("e-mail", "email addresses", "textEmailAddress", "textemail"),

            new Hint("phone", "phone numbers", "phone", "phone"),
            new Hint("tel", "phone numbers", "phone", "phone"),
            new Hint("mobile", "phone numbers", "phone", "phone"),

            new Hint("password", "passwords", "textPassword", "textpassword"),
            new Hint("pwd", "passwords", "textPassword", "textpassword"),
            new Hint("passwd", "passwords", "textPassword", "textpassword"),

            new Hint("url", "URLs", "textUri", "texturi"),
            new Hint("uri", "URLs", "textUri", "texturi"),

            new Hint("number", "numbers", "number", "number"),
            new Hint("num", "numbers", "number", "number"),
            new Hint("zip", "numbers", "number", "number"),
            new Hint("pin", "numbers", "number", "number"),

            new Hint("name", "names", "textPersonName", "textpersonname"),
            new Hint("person", "names", "textPersonName", "textpersonname")
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.EDIT_TEXT,
                SdkConstants.AUTO_COMPLETE_TEXT_VIEW,
                SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW,
                SdkConstants.EXTRACT_EDIT_TEXT,
                "com.google.android.material.textfield.TextInputEditText",
                "android.support.design.widget.TextInputEditText",
                "TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_INPUT_TYPE);

        if (inputType == null || inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "EditText is missing an `inputType` attribute");
            return;
        }

        if (inputType.startsWith("@")) {
            return;
        }

        String lowerInputType = inputType.toLowerCase(Locale.US);
        if ("text".equals(lowerInputType.trim())) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String localId = id.substring(id.lastIndexOf('/') + 1).toLowerCase(Locale.US);
        if (localId.isEmpty()) {
            return;
        }

        for (Hint hint : HINTS) {
            if (localId.contains(hint.keyword)) {
                if (!containsType(lowerInputType, hint.matches)) {
                    String message = String.format(
                            "The id of this text field (`%s`) suggests it should accept %s; "
                                    + "consider setting `inputType` to `%s`",
                            id, hint.display, hint.expected);
                    Attr inputTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI,
                            SdkConstants.ATTR_INPUT_TYPE);
                    if (inputTypeAttr != null) {
                        context.report(ISSUE, inputTypeAttr,
                                context.getLocation(inputTypeAttr), message);
                    } else {
                        context.report(ISSUE, element, context.getLocation(element), message);
                    }
                }
            }
        }
    }

    private static boolean containsType(String inputType, String... types) {
        for (String token : inputType.split("\\|")) {
            String trimmed = token.trim();
            for (String type : types) {
                if (trimmed.contains(type)) {
                    return true;
                }
            }
        }
        return false;
    }
}