package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AccessibilityDetector extends ResourceXmlDetector {

    private static final String QUICK_CONTACT_BADGE = "QuickContactBadge";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without `contentDescription`",
            "Non-textual widgets such as `ImageView` and `ImageButton` should use the "
                    + "`android:contentDescription` attribute to provide a textual description "
                    + "for screen readers and other accessibility tools.\n\n"
                    + "Purely decorative images that do not provide content or enable user "
                    + "actions should set the description to `@null`, or, when `minSdkVersion` "
                    + "is 16 or higher, set `android:importantForAccessibility` to `no`.\n\n"
                    + "For text fields, do not set both `android:hint` and "
                    + "`android:contentDescription`; the hint will never be shown. Set only the "
                    + "hint.",
            Category.A11Y,
            3,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_IMAGE_VIEW,
                SdkConstants.TAG_IMAGE_BUTTON,
                QUICK_CONTACT_BADGE,
                SdkConstants.TAG_EDIT_TEXT
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (SdkConstants.TAG_EDIT_TEXT.equals(tag)) {
            checkTextField(context, element);
        } else {
            checkImage(context, element);
        }
    }

    private static void checkImage(XmlContext context, Element element) {
        if (hasContentDescription(element)) {
            return;
        }

        if (SdkConstants.VALUE_NO.equals(
                element.getAttributeNS(SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY))
                && context.getMainProject().getMinSdk() >= 16) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
                "Missing `contentDescription` attribute on image");
    }

    private static void checkTextField(XmlContext context, Element element) {
        String contentDescription = element.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_CONTENT_DESCRIPTION);
        String hint = element.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_HINT);

        if (!contentDescription.isEmpty()
                && !SdkConstants.VALUE_NULL.equals(contentDescription)
                && !hint.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Do not set both `hint` and `contentDescription` on a text field; "
                            + "the hint will not be shown");
        }
    }

    private static boolean hasContentDescription(Element element) {
        String description = element.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_CONTENT_DESCRIPTION);
        return !description.isEmpty() && !SdkConstants.VALUE_NULL.equals(description);
    }
}