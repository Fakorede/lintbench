package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AccessibilityDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without contentDescription",
            "Non-textual widgets like ImageViews and ImageButtons should use the " +
            "`contentDescription` attribute to specify a textual description of the widget " +
            "such that screen readers and other accessibility tools can adequately describe " +
            "the user interface.\n\n" +
            "Note that elements in application screens that are purely decorative and do not " +
            "provide any content or enable a user action should not have accessibility content " +
            "descriptions. In this case, set their descriptions to `@null`. If your app's " +
            "minSdkVersion is 16 or higher, you can instead set these graphical elements' " +
            "`android:importantForAccessibility` attributes to `no`.\n\n" +
            "Note that for text fields, you should not set both the `hint` and the " +
            "`contentDescription` attributes since the hint will never be shown. Just set the `hint`.",
            Category.ACCESSIBILITY,
            3,
            Severity.WARNING,
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.IMAGE_VIEW,
                SdkConstants.IMAGE_BUTTON
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr contentDescription = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION);
        if (contentDescription != null) {
            String value = contentDescription.getValue();
            if (value != null && !value.isEmpty() && !value.equals("@null")) {
                return;
            }
            if (value != null && value.equals("@null")) {
                return;
            }
        }

        Attr important = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY);
        if (important != null) {
            String value = important.getValue();
            if ("no".equals(value) || "noHideDescendants".equals(value)) {
                return;
            }
        }

        context.report(ISSUE, element, context.getLocation(element),
                "Missing `contentDescription` attribute on image");
    }
}