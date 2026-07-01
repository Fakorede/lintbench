package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AutofillDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher, " +
            "or explicitly specify that the view is not important for autofill. Your app can " +
            "help an autofill service classify the data correctly by providing the meaning of " +
            "each view that could be autofillable, such as views representing usernames, " +
            "passwords, credit card fields, email addresses, etc.\n\n" +
            "The hints can have any value, but it is recommended to use predefined values like " +
            "'username' for a username or 'creditCardNumber' for a credit card number. For a " +
            "list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` " +
            "constants in the `View` reference at " +
            "https://developer.android.com/reference/android/view/View.html.\n\n" +
            "You can mark a view unimportant for autofill by specifying an " +
            "`importantForAutofill` attribute on that view or a parent view. See " +
            "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String AUTOFILL_HINTS = "autofillHints";
    private static final String IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    private static final Set<String> AUTOFILLABLE_VIEWS = new HashSet<>(Arrays.asList(
            "EditText",
            "ExtractEditText",
            "AutoCompleteTextView",
            "MultiAutoCompleteTextView",
            "SearchAutoComplete",
            "AppCompatAutoCompleteTextView",
            "MaterialAutoCompleteTextView",
            "TextInputEditText"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return AUTOFILLABLE_VIEWS;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, AUTOFILL_HINTS)) {
            return;
        }

        if (isAutofillDisabled(element)) {
            return;
        }

        if (hasAutofillDisabledAncestor(element)) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
                "Missing autofillHints attribute");
    }

    private static boolean isAutofillDisabled(@NonNull Element element) {
        String important = element.getAttributeNS(ANDROID_URI, IMPORTANT_FOR_AUTOFILL);
        return isNoAutofillValue(important);
    }

    private static boolean hasAutofillDisabledAncestor(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            if (isAutofillDisabled(parentElement)) {
                return true;
            }
            parent = parentElement.getParentNode();
        }
        return false;
    }

    private static boolean isNoAutofillValue(@NonNull String value) {
        return "no".equals(value) || "noExcludeDescendants".equals(value);
    }
}