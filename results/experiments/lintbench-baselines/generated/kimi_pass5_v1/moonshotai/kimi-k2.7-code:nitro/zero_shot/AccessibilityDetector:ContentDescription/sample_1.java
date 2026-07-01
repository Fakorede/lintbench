package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_HINT;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY;
import static com.android.SdkConstants.VALUE_NULL;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

public class AccessibilityDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without `contentDescription`",
            "Non-textual widgets like ImageViews and ImageButtons should use the "
                    + "`contentDescription` attribute to specify a textual description of "
                    + "the widget such that screen readers and other accessibility tools "
                    + "can adequately describe the user interface.\n\n"
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
            3,
            Severity.WARNING,
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/guide/topics/ui/accessibility/apps#special-cases");

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (tag.endsWith("ImageView") || tag.endsWith("ImageButton")
                || tag.endsWith("FloatingActionButton")) {
            checkImage(context, element);
        } else if (tag.endsWith("EditText")) {
            checkEditText(context, element);
        }
    }

    private void checkImage(XmlContext context, Element element) {
        boolean hasContentDescription =
                element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);
        if (hasContentDescription) {
            String description = element.getAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);
            if (!VALUE_NULL.equals(description)) {
                return;
            }
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY)) {
            String importantForAccessibility =
                    element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
            if ("no".equals(importantForAccessibility)) {
                return;
            }
        }

        context.report(ISSUE, element, context.getLocation(element),
                "Missing `contentDescription` attribute on image", null);
    }

    private void checkEditText(XmlContext context, Element element) {
        boolean hasHint = element.hasAttributeNS(ANDROID_URI, ATTR_HINT);
        boolean hasContentDescription =
                element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);
        if (hasHint && hasContentDescription) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Don't set both `hint` and `contentDescription` on EditText; just set `hint`",
                    null);
        }
    }
}