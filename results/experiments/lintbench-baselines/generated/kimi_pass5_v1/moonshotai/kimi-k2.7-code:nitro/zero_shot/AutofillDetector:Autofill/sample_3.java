package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends ResourceXmlDetector {
    private static final String AUTO_FILL_HINTS = "autofillHints";
    private static final String IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    private static final int AUTOFILL_MIN_TARGET_SDK = 26;

    private static final Implementation IMPLEMENTATION = new Implementation(
            AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Missing `autofillHints` attribute",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher "
                    + "or explicitly specify that the view is not important for autofill. "
                    + "Your app can help an autofill service classify the data correctly by "
                    + "providing the meaning of each view that could be autofillable, such as "
                    + "views representing usernames, passwords, credit card fields, email "
                    + "addresses, etc.\n\n"
                    + "The hints can have any value, but it is recommended to use predefined "
                    + "values like 'username' for a username or 'creditCardNumber' for a credit "
                    + "card number. For a list of all predefined autofill hint constants, see the "
                    + "`AUTOFILL_HINT_` constants in the `View` reference.\n\n"
                    + "You can mark a view unimportant for autofill by specifying an "
                    + "`importantForAutofill` attribute on that view or a parent view.",
            "https://developer.android.com/guide/topics/text/autofill.html",
            Category.USABILITY,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "AutoCompleteTextView",
                "EditText",
                "ExtractEditText",
                "MultiAutoCompleteTextView",
                "SearchView",
                "TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getProject().getTargetSdk() < AUTOFILL_MIN_TARGET_SDK) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, AUTO_FILL_HINTS)) {
            return;
        }

        if (isNotImportantForAutofill(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute");
    }

    private static boolean isNotImportantForAutofill(@NonNull Element element) {
        String value = element.getAttributeNS(ANDROID_URI, IMPORTANT_FOR_AUTOFILL);
        if (!value.isEmpty()) {
            int mode = parseImportantForAutofill(value);
            return mode == AutofillMode.NO
                    || mode == AutofillMode.NO_EXCLUDE_DESCENDANTS;
        }

        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            String parentValue = parentElement.getAttributeNS(ANDROID_URI, IMPORTANT_FOR_AUTOFILL);
            if (!parentValue.isEmpty()) {
                int mode = parseImportantForAutofill(parentValue);
                if (mode == AutofillMode.NO
                        || mode == AutofillMode.NO_EXCLUDE_DESCENDANTS
                        || mode == AutofillMode.YES_EXCLUDE_DESCENDANTS) {
                    return true;
                }
            }
            parent = parentElement.getParentNode();
        }

        return false;
    }

    private static int parseImportantForAutofill(@NonNull String value) {
        switch (value) {
            case "yes":
                return AutofillMode.YES;
            case "no":
                return AutofillMode.NO;
            case "auto":
                return AutofillMode.AUTO;
            case "yesExcludeDescendants":
                return AutofillMode.YES_EXCLUDE_DESCENDANTS;
            case "noExcludeDescendants":
                return AutofillMode.NO_EXCLUDE_DESCENDANTS;
            default:
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return AutofillMode.AUTO;
                }
        }
    }

    private static final class AutofillMode {
        static final int AUTO = 0;
        static final int YES = 1;
        static final int NO = 2;
        static final int YES_EXCLUDE_DESCENDANTS = 4;
        static final int NO_EXCLUDE_DESCENDANTS = 8;

        private AutofillMode() {}
    }
}