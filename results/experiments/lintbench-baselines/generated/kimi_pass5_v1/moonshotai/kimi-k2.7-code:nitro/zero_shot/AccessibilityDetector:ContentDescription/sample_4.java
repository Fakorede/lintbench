package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_CONTENT_DESCRIPTION = "contentDescription";
    private static final String ATTR_HINT = "hint";
    private static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";
    private static final String IMAGE_VIEW = "ImageView";
    private static final String IMAGE_BUTTON = "ImageButton";
    private static final String EDIT_TEXT = "EditText";

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
        return Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON, EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (IMAGE_VIEW.equals(tagName) || IMAGE_BUTTON.equals(tagName)) {
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
                    && !isHiddenForAccessibility(context, element)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Missing `contentDescription` attribute on image");
            }
        } else if (EDIT_TEXT.equals(tagName)) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)
                    && element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Do not set both `hint` and `contentDescription` on a text field; just set `hint`");
            }
        }
    }

    private static boolean isHiddenForAccessibility(
            @NonNull XmlContext context, @NonNull Element element) {
        String important =
                element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
        return "no".equals(important) && context.getMainProject().getMinSdk() >= 16;
    }
}