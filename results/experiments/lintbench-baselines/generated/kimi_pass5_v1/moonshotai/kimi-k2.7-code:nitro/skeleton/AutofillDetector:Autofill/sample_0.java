package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_AUTOFILL_TYPE = "autofillType";

    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";
    private static final String VALUE_YES_EXCLUDE_DESCENDANTS = "yesExcludeDescendants";
    private static final String VALUE_AUTOFILL_TYPE_NONE = "none";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                            + "higher or explicitly specify that the view is not important for "
                            + "autofill. Your app can help an autofill service classify the data "
                            + "correctly by providing the meaning of each view that could be "
                            + "autofillable, such as views representing usernames, passwords, "
                            + "credit card fields, email addresses, etc.\n\n"
                            + "The hints can have any value, but it is recommended to use predefined "
                            + "values like 'username' for a username or 'creditCardNumber' for a "
                            + "credit card number. For a list of all predefined autofill hint "
                            + "constants, see the `AUTOFILL_HINT_` constants in the `View` reference "
                            + "at https://developer.android.com/reference/android/view/View.html.\n\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. See "
                            + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getMainProject().getTargetSdkVersion().getFeatureLevel() < 26) {
            return;
        }

        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        if (isExcludedByAncestor(element)) {
            return;
        }

        if (hasAutofillHints(element)) {
            return;
        }

        if (!isAutofillableView(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Specify an autofillHints attribute or explicitly specify that the view is not important for autofill.");
    }

    private boolean isAutofillableView(@NonNull Element element) {
        String autofillType = element.getAttributeNS(ANDROID_URI, ATTR_AUTOFILL_TYPE);
        if (VALUE_AUTOFILL_TYPE_NONE.equals(autofillType)) {
            return false;
        }
        if (!autofillType.isEmpty()) {
            return true;
        }

        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }

        if (tag.endsWith("EditText") || tag.endsWith("AutoCompleteTextView")) {
            return true;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE)) {
            return true;
        }

        return false;
    }

    private boolean hasAutofillHints(@NonNull Element element) {
        String hints = element.getAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS);
        return hints != null && !hints.trim().isEmpty();
    }

    private boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        return VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value);
    }

    private boolean isExcludedByAncestor(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element ancestor = (Element) parent;
            String value = ancestor.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (VALUE_NO.equals(value)
                    || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)
                    || VALUE_YES_EXCLUDE_DESCENDANTS.equals(value)) {
                return true;
            }
            parent = ancestor.getParentNode();
        }
        return false;
    }
}