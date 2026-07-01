package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher, or "
                    + "explicitly specify that the view is not important for autofill. Your app "
                    + "can help an autofill service classify the data correctly by providing the "
                    + "meaning of each view that could be autofillable, such as views representing "
                    + "usernames, passwords, credit card fields, email addresses, etc.\n\n"
                    + "The hints can have any value, but it is recommended to use predefined values "
                    + "like 'username' for a username or 'creditCardNumber' for a credit card "
                    + "number.\n\n"
                    + "You can mark a view unimportant for autofill by specifying an "
                    + "`importantForAutofill` attribute on that view or a parent view.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Set<String> AUTOFILLABLE_TAGS = new HashSet<>(Arrays.asList(
            SdkConstants.AUTO_COMPLETE_TEXT_VIEW,
            SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW,
            SdkConstants.SEARCH_VIEW,
            SdkConstants.EXTRACT_EDIT_TEXT,
            "SearchAutoComplete"));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        if (!isAutofillable(element)) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTOFILL_HINTS)) {
            return;
        }

        if (!isImportantForAutofill(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `autofillHints` attribute. Either supply autofill hints, or set "
                        + "`importantForAutofill` to \"no\" on this view or a parent view.");
    }

    private static boolean isAutofillable(Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            return false;
        }

        String simpleName;
        if (SdkConstants.VIEW_TAG.equals(tag)) {
            String cls = element.getAttribute(SdkConstants.ATTR_CLASS);
            int dot = cls.lastIndexOf('.');
            simpleName = dot == -1 ? cls : cls.substring(dot + 1);
        } else {
            int dot = tag.lastIndexOf('.');
            simpleName = dot == -1 ? tag : tag.substring(dot + 1);
        }

        if (simpleName.endsWith("EditText")) {
            return true;
        }

        if (AUTOFILLABLE_TAGS.contains(simpleName)) {
            return true;
        }

        return element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
    }

    private static boolean isImportantForAutofill(Element element) {
        String value = element.getAttributeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);

        if (!value.isEmpty()) {
            return !(SdkConstants.VALUE_NO.equals(value)
                    || SdkConstants.VALUE_NO_EXCLUDE_DESCENDANTS.equals(value));
        }

        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            String parentValue = parentElement.getAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL);

            if (!parentValue.isEmpty()
                    && (SdkConstants.VALUE_NO_EXCLUDE_DESCENDANTS.equals(parentValue)
                            || SdkConstants.VALUE_YES_EXCLUDE_DESCENDANTS.equals(parentValue))) {
                return false;
            }

            parent = parent.getParentNode();
        }

        return true;
    }
}