package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AccessibilityDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without `contentDescription`",
            "Non-textual widgets like ImageViews and ImageButtons should use the "
                    + "`contentDescription` attribute to specify a textual description of the "
                    + "widget such that screen readers and other accessibility tools can "
                    + "adequately describe the user interface.\n\n"
                    + "Note that elements in application screens that are purely decorative "
                    + "and do not provide any content or enable a user action should not have "
                    + "accessibility content descriptions. In this case, set their descriptions "
                    + "to `@null`. If your app's minSdkVersion is 16 or higher, you can instead "
                    + "set these graphical elements' `android:importantForAccessibility` "
                    + "attributes to `no`.\n\n"
                    + "Note that for text fields, you should not set both the `hint` and the "
                    + "`contentDescription` attributes since the hint will never be shown. Just "
                    + "set the `hint`.",
            Category.A11Y,
            5,
            Severity.WARNING,
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.IMAGE_VIEW,
                SdkConstants.IMAGE_BUTTON,
                SdkConstants.EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (SdkConstants.IMAGE_VIEW.equals(tag) || SdkConstants.IMAGE_BUTTON.equals(tag)) {
            if (element.hasAttributeNS(
                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION)) {
                String description = element.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION);

                if (SdkConstants.VALUE_NULL.equals(description)) {
                    return;
                }

                if (description.isEmpty()) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Empty `contentDescription` attribute on image; "
                                    + "use a meaningful description or set it to `@null` "
                                    + "for decorative elements");
                }
                return;
            }

            if (context.getMainProject().getMinSdk() >= 16) {
                String important = element.getAttributeNS(
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY);
                if (SdkConstants.VALUE_NO.equals(important)) {
                    return;
                }
            }

            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `contentDescription` attribute on image");
        } else if (SdkConstants.EDIT_TEXT.equals(tag)) {
            boolean hasHint = element.hasAttributeNS(
                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_HINT);
            boolean hasContentDescription = element.hasAttributeNS(
                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION);

            if (hasHint && hasContentDescription) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Don't set both `hint` and `contentDescription` on a text field; "
                                + "the hint will never be shown. Just set `hint`.");
            }
        }
    }
}