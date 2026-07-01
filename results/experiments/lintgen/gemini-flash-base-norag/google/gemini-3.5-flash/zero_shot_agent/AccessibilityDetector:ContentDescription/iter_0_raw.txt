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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AccessibilityDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without `contentDescription`",
            "Non-textual widgets like ImageViews and ImageButtons should use the " +
            "`contentDescription` attribute to specify a textual description of " +
            "the widget such that screen readers and other accessibility tools " +
            "can adequately describe the user interface.\n\n" +
            "Note that elements in application screens that are purely decorative " +
            "and do not provide any content or enable a user action should not " +
            "have accessibility content descriptions. In this case, set their " +
            "descriptions to `@null`. If your app's minSdkVersion is 16 or higher, " +
            "you can instead set these graphical elements' " +
            "`android:importantForAccessibility` attributes to `no`.\n\n" +
            "Note that for text fields, you should not set both the `hint` and the " +
            "`contentDescription` attributes since the hint will never be shown. Just " +
            "set the `hint`.",
            Category.A11Y,
            4,
            Severity.WARNING,
            new Implementation(
                    AccessibilityDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.IMAGE_VIEW,
                "ImageButton",
                "EditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (tagName.equals(SdkConstants.IMAGE_VIEW) || tagName.equals("ImageButton")) {
            checkImage(context, element);
        } else if (tagName.equals("EditText") || tagName.endsWith("EditText")) {
            checkTextField(context, element);
        }
    }

    private void checkImage(XmlContext context, Element element) {
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION)) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "importantForAccessibility")) {
            String important = element.getAttributeNS(SdkConstants.ANDROID_URI, "importantForAccessibility");
            if ("no".equals(important) || "noHideDescendants".equals(important)) {
                return;
            }
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "[Accessibility] Missing `contentDescription` attribute on image"
        );
    }

    private void checkTextField(XmlContext context, Element element) {
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION)
                && element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_HINT)) {
            Attr contentDescriptionNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION);
            context.report(
                    ISSUE,
                    contentDescriptionNode,
                    context.getLocation(contentDescriptionNode),
                    "Do not set both `contentDescription` and `hint` on text fields, " +
                    "since the `hint` will never be shown. Just set the `hint`."
            );
        }
    }
}