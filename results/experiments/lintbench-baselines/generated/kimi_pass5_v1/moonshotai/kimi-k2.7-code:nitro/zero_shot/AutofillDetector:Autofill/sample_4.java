package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.VALUE_NO;
import static com.android.SdkConstants.VALUE_NO_EXCLUDE_DESCENDANTS;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher "
                    + "or explicitly specify that the view is not important for autofill. "
                    + "Your app can help an autofill service classify the data correctly by "
                    + "providing the meaning of each view that could be autofillable, such as "
                    + "views representing usernames, passwords, credit card fields, email "
                    + "addresses, etc.\n\n"
                    + "The hints can have any value, but it is recommended to use predefined "
                    + "values like 'username' for a username or 'creditCardNumber' for a credit "
                    + "card number. For a list of all predefined autofill hint constants, see "
                    + "the `AUTOFILL_HINT_` constants in the `View` reference at "
                    + "https://developer.android.com/reference/android/view/View.html.\n\n"
                    + "You can mark a view unimportant for autofill by specifying an "
                    + "`importantForAutofill` attribute on that view or a parent view. See "
                    + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mTargetSdk;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(EDIT_TEXT, AUTO_COMPLETE_TEXT_VIEW, MULTI_AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mTargetSdk = context.getMainProject().getTargetSdk();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mTargetSdk < 26) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        if (isUnimportant(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Consider specifying `autofillHints` or setting `importantForAutofill` to \"no\"");
    }

    private static boolean isUnimportant(@NonNull Element element) {
        while (element != null) {
            String important = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (important != null && !important.isEmpty()) {
                return VALUE_NO.equals(important) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(important);
            }
            Node parent = element.getParentNode();
            if (parent instanceof Element) {
                element = (Element) parent;
            } else {
                break;
            }
        }
        return false;
    }
}