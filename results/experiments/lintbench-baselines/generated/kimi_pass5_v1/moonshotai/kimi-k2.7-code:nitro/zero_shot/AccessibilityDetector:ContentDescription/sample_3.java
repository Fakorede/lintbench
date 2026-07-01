package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_HINT;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY;
import static com.android.SdkConstants.IMAGE_BUTTON;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.TAG_EDIT_TEXT;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AccessibilityDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AccessibilityDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without `contentDescription`",
            "Non-textual widgets like ImageViews and ImageButtons should use the "
                    + "`contentDescription` attribute to specify a textual description of the "
                    + "widget such that screen readers and other accessibility tools can "
                    + "adequately describe the user interface.\n"
                    + "\n"
                    + "Note that elements in application screens that are purely decorative and "
                    + "do not provide any content or enable a user action should not have "
                    + "accessibility content descriptions. In this case, set their descriptions "
                    + "to `@null`. If your app's minSdkVersion is 16 or higher, you can instead "
                    + "set these graphical elements' `android:importantForAccessibility` "
                    + "attributes to `no`.\n"
                    + "\n"
                    + "Note that for text fields, you should not set both the `hint` and the "
                    + "`contentDescription` attributes since the hint will never be shown. Just "
                    + "set the `hint`.",
            Category.A11Y,
            3,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON, TAG_EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (IMAGE_VIEW.equals(tag) || IMAGE_BUTTON.equals(tag)) {
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
                    && !isImportantForAccessibilityNo(element)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing `contentDescription` attribute on image");
            }
        } else if (TAG_EDIT_TEXT.equals(tag)) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)
                    && element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Do not set both `hint` and `contentDescription` on a text field");
            }
        }
    }

    private static boolean isImportantForAccessibilityNo(@NonNull Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
        return "no".equals(value);
    }
}