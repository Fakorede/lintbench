package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY;
import static com.android.SdkConstants.TAG_IMAGE_BUTTON;
import static com.android.SdkConstants.TAG_IMAGE_VIEW;
import static com.android.SdkConstants.VALUE_NO;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AccessibilityDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without `contentDescription`",
            "Non-textual widgets like ImageViews and ImageButtons should use the `contentDescription` attribute "
                    + "to specify a textual description of the widget such that screen readers and other "
                    + "accessibility tools can adequately describe the user interface.\n\n"
                    + "Note that elements in application screens that are purely decorative and do not provide "
                    + "any content or enable a user action should not have accessibility content descriptions. "
                    + "In this case, set their descriptions to `@null`. If your app's minSdkVersion is 16 or higher, "
                    + "you can instead set these graphical elements' `android:importantForAccessibility` attributes "
                    + "to `no`.\n\n"
                    + "Note that for text fields, you should not set both the `hint` and the `contentDescription` "
                    + "attributes since the hint will never be shown. Just set the `hint`.",
            Category.ACCESSIBILITY,
            3,
            Severity.WARNING,
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_IMAGE_VIEW, TAG_IMAGE_BUTTON);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String contentDescription = element.getAttribute(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);
        if (contentDescription == null || contentDescription.isEmpty()) {
            String importantForAccessibility = element.getAttribute(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
            if (VALUE_NO.equals(importantForAccessibility)) {
                return;
            }

            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `android:contentDescription` attribute on image"
            );
        }
    }
}