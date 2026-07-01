package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
        new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
            SdkConstants.TAG_IMAGE_VIEW,
            SdkConstants.TAG_IMAGE_BUTTON
        );
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String contentDescription = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION);
        if (!contentDescription.isEmpty()) {
            if (contentDescription.equals(SdkConstants.VALUE_NULL)) {
                return;
            }
            return;
        }

        String importantForAccessibility = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY);
        if ("no".equals(importantForAccessibility)) {
            int minSdk = context.getProject().getMinSdk();
            if (minSdk >= 16) {
                return;
            }
        }

        context.report(ISSUE, element, context.getLocation(element),
            "Missing `contentDescription` attribute on image");
    }
}