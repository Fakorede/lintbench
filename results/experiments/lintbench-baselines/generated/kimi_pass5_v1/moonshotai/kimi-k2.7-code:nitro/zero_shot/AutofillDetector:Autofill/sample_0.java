package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher, "
                    + "or explicitly specify that the view is not important for autofill. "
                    + "Your app can help an autofill service classify the data correctly by "
                    + "providing the meaning of each view that could be autofillable, such as "
                    + "views representing usernames, passwords, credit card fields, email "
                    + "addresses, etc.\n\n"
                    + "The hints can have any value, but it is recommended to use predefined "
                    + "values like `username` for a username or `creditCardNumber` for a credit "
                    + "card number. For a list of all predefined autofill hint constants, see the "
                    + "`AUTOFILL_HINT_` constants in the `View` reference.\n\n"
                    + "You can mark a view unimportant for autofill by specifying an "
                    + "`importantForAutofill` attribute on that view or a parent view. See "
                    + "`View#setImportantForAutofill(int)`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public @NotNull Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        if (!couldBeAutofillable(element)) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        if (isUnimportantForAutofill(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `autofillHints` attribute: for better autofill support, specify "
                        + "`android:autofillHints` or set `android:importantForAutofill` to `no`",
                createFix());
    }

    private static boolean couldBeAutofillable(@NotNull Element element) {
        String tag = element.getTagName();
        int index = tag.lastIndexOf('.');
        String simpleName = index >= 0 ? tag.substring(index + 1) : tag;
        return simpleName.endsWith("EditText") || simpleName.endsWith("AutoCompleteTextView");
    }

    private static boolean isUnimportantForAutofill(@NotNull Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (isNoOrNoExcludeDescendants(value)) {
            return true;
        }

        Element parent = getParentElement(element);
        while (parent != null) {
            String parentValue = parent.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (isNoOrNoExcludeDescendants(parentValue)) {
                return true;
            }
            parent = getParentElement(parent);
        }

        return false;
    }

    private static boolean isNoOrNoExcludeDescendants(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.equals("no")
                || trimmed.equals("noExcludeDescendants")
                || trimmed.equals("2")
                || trimmed.equals("8");
    }

    @Nullable
    private static Element getParentElement(@NotNull Element element) {
        Node parent = element.getParentNode();
        return parent instanceof Element ? (Element) parent : null;
    }

    private static @NotNull LintFix createFix() {
        return LintFix.create()
                .set(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL, "no")
                .build();
    }
}