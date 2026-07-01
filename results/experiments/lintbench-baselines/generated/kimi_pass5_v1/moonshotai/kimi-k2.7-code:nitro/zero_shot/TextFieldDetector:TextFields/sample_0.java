package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.EXTRACT_EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class TextFieldDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability because depending on the data to be input, optimized keyboards can be shown to the user (such as just digits and parentheses for a phone number).\n\n"
                    + "The lint detector also looks at the `id` of the view, and if the `id` offers a hint of the purpose of the field (for example, the `id` contains the phrase `phone` or `email`), then lint will also ensure that the `inputType` contains the corresponding type attributes.\n\n"
                    + "If you really want to keep the text field generic, you can suppress this warning by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Collection<String> TEXT_FIELDS = Arrays.asList(
            EDIT_TEXT,
            AUTO_COMPLETE_TEXT_VIEW,
            MULTI_AUTO_COMPLETE_TEXT_VIEW,
            EXTRACT_EDIT_TEXT
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return TEXT_FIELDS;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType`");
            return;
        }

        Set<String> types = parseInputTypes(inputType);
        if (types.size() == 1 && types.contains("text")) {
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id.isEmpty()) {
            return;
        }

        String idName = getIdName(id);
        if (idName == null) {
            return;
        }

        checkIdHints(context, element, types, idName.toLowerCase(Locale.US));
    }

    @NonNull
    private Set<String> parseInputTypes(@NonNull String inputType) {
        Set<String> types = new HashSet<>();
        for (String type : inputType.split("\\|")) {
            types.add(type.trim());
        }
        return types;
    }

    private String getIdName(@NonNull String id) {
        int slash = id.lastIndexOf('/');
        if (slash == -1 || slash == id.length() - 1) {
            return null;
        }
        return id.substring(slash + 1);
    }

    private void checkIdHints(@NonNull XmlContext context, @NonNull Element element,
            @NonNull Set<String> types, @NonNull String idName) {
        if (idName.contains("phone") && !types.contains("phone")) {
            reportTypeMismatch(context, element, "a phone number", "\"phone\"");
        }

        if ((idName.contains("email") || idName.contains("e_mail"))
                && !containsAny(types, "textEmailAddress", "textWebEmailAddress")) {
            reportTypeMismatch(context, element, "an email", "\"textEmailAddress\"");
        }

        if ((idName.contains("pass") || idName.contains("passwd") || idName.contains("pwd"))
                && !containsAny(types, "textPassword", "numberPassword",
                        "textVisiblePassword", "textWebPassword")) {
            reportTypeMismatch(context, element, "a password", "a password `inputType`");
        }

        if ((idName.contains("url") || idName.contains("uri"))
                && !containsAny(types, "textUri", "textWebUri")) {
            reportTypeMismatch(context, element, "a URL", "\"textUri\"");
        }

        if ((idName.contains("number") || idName.contains("amount") || idName.contains("digit"))
                && !containsNumberType(types)) {
            reportTypeMismatch(context, element, "a number", "a number `inputType`");
        }

        if (idName.contains("search")
                && !containsAny(types, "textFilter", "textWebEditText")) {
            reportTypeMismatch(context, element, "a search", "\"textFilter\"");
        }

        if (idName.contains("address") && !idName.contains("emailaddress")
                && !containsAny(types, "textPostalAddress", "textWebPostalAddress")) {
            reportTypeMismatch(context, element, "an address", "\"textPostalAddress\"");
        }
    }

    private boolean containsAny(@NonNull Set<String> types, @NonNull String... expected) {
        for (String type : expected) {
            if (types.contains(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNumberType(@NonNull Set<String> types) {
        for (String type : types) {
            if (type.startsWith("number")) {
                return true;
            }
        }
        return false;
    }

    private void reportTypeMismatch(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String purpose, @NonNull String expectedType) {
        String message = "The id of this field suggests it should be " + purpose
                + " field, but the `inputType` attribute does not include " + expectedType;
        context.report(ISSUE, element, context.getAttributeLocation(element, ATTR_INPUT_TYPE),
                message);
    }
}