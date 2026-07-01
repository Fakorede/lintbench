package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_HINT;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.IMAGE_BUTTON;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.VALUE_NO;
import static com.android.SdkConstants.VALUE_NULL;

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
        return Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON, EDIT_TEXT);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        if (EDIT_TEXT.equals(tag)) {
            checkTextField(context, element);
        } else {
            checkImageView(context, element);
        }
    }

    private void checkImageView(@NotNull XmlContext context, @NotNull Element element) {
        String contentDescription = element.getAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);

        if (contentDescription != null && !contentDescription.isEmpty()) {
            if (VALUE_NULL.equals(contentDescription)) {
                return;
            }
            return;
        }

        String importantForAccessibility = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
        if (VALUE_NO.equals(importantForAccessibility)) {
            int minSdk = context.getMainProject().getMinSdk();
            if (minSdk >= 16) {
                return;
            }
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `contentDescription` attribute on image"
        );
    }

    private void checkTextField(@NotNull XmlContext context, @NotNull Element element) {
        String hint = element.getAttributeNS(ANDROID_URI, ATTR_HINT);
        String contentDescription = element.getAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);

        boolean hasHint = hint != null && !hint.isEmpty();
        boolean hasContentDescription = contentDescription != null && !contentDescription.isEmpty();

        if (hasHint && hasContentDescription) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Do not set both `hint` and `contentDescription` on a text field. " +
                    "The hint will never be shown. Just set the `hint`."
            );
        }
    }
}