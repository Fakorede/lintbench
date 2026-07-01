package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class TextFieldDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            TextFieldDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `android:inputType` attribute on a text field improves usability "
                    + "because optimized keyboards can be shown to the user based on the data "
                    + "to be input (such as just digits and parentheses for a phone number). "
                    + "If you really want to keep the text field generic, you can suppress "
                    + "this warning by setting `android:inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    private static final String ANDROID_URI = SdkConstants.ANDROID_URI;
    private static final String ATTR_INPUT_TYPE = SdkConstants.ATTR_INPUT_TYPE;
    private static final String ATTR_ID = SdkConstants.ATTR_ID;

    private static final class Hint {
        final String idPhrase;
        final String display;
        final List<String> inputTypes;

        Hint(String idPhrase, String display, String... inputTypes) {
            this.idPhrase = idPhrase;
            this.display = display;
            this.inputTypes = Arrays.asList(inputTypes);
        }
    }

    private static final List<Hint> HINTS = Arrays.asList(
            new Hint("phone", "phone", "phone"),
            new Hint("email", "email", "textEmailAddress", "textWebEmailAddress"),
            new Hint("password", "password",
                    "textPassword", "textVisiblePassword", "numberPassword", "textWebPassword"),
            new Hint("passwd", "password",
                    "textPassword", "textVisiblePassword", "numberPassword", "textWebPassword"),
            new Hint("pass", "password",
                    "textPassword", "textVisiblePassword", "numberPassword", "textWebPassword"),
            new Hint("url", "URL", "textUri"),
            new Hint("uri", "URI", "textUri"),
            new Hint("date", "date", "date", "datetime"),
            new Hint("time", "time", "time", "datetime"));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "EditText",
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView",
                "ExtractEditText",
                "TextInputEditText");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputTypeAttr == null) {
            context.report(ISSUE, element, context.getLocation(element),
                    "To improve usability, consider adding an `android:inputType` attribute to this text field.");
            return;
        }

        String inputType = inputTypeAttr.getValue();
        if (inputType == null || inputType.trim().isEmpty()) {
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                    "To improve usability, consider adding an `android:inputType` attribute to this text field.");
            return;
        }

        // Allow an explicit generic text type to suppress the check.
        if ("text".equals(inputType)) {
            return;
        }

        String idValue = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        String idName = getIdName(idValue);
        if (idName == null) {
            return;
        }

        String[] tokens = inputType.split("\\|");
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].trim().toLowerCase(Locale.US);
        }

        String lowerId = idName.toLowerCase(Locale.US);
        for (Hint hint : HINTS) {
            if (lowerId.contains(hint.idPhrase)) {
                boolean found = false;
                for (String required : hint.inputTypes) {
                    String lowerRequired = required.toLowerCase(Locale.US);
                    for (String token : tokens) {
                        if (token.contains(lowerRequired)) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                }
                if (!found) {
                    String expected = hint.inputTypes.get(0);
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            String.format("The id of this text field suggests it is a %1$s field; "
                                    + "consider adding `%2$s` to `android:inputType` "
                                    + "(or set `android:inputType=\"text\"` to keep it generic).",
                                    hint.display, expected));
                }
            }
        }
    }

    private static String getIdName(String idValue) {
        if (idValue == null || idValue.isEmpty()) {
            return null;
        }
        int slash = idValue.lastIndexOf('/');
        if (slash != -1 && slash + 1 < idValue.length()) {
            return idValue.substring(slash + 1);
        }
        return idValue;
    }
}