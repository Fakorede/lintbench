package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
            "`contentDescription` attributes since the hint will never be shown. " +
            "Just set the `hint`.",
            Category.A11Y,
            3,
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
                SdkConstants.IMAGE_BUTTON,
                "QuickContactBadge",
                SdkConstants.EDIT_TEXT,
                "android.widget.ImageView",
                "android.widget.ImageButton",
                "android.widget.EditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (isImageView(tag)) {
            checkImageView(context, element);
        } else if (isEditTextView(tag)) {
            checkEditTextView(context, element);
        }
    }

    private boolean isImageView(String tag) {
        return tag.equals(SdkConstants.IMAGE_VIEW) 
                || tag.equals(SdkConstants.IMAGE_BUTTON) 
                || tag.equals("QuickContactBadge")
                || tag.endsWith(".ImageView") 
                || tag.endsWith(".ImageButton") 
                || tag.endsWith(".QuickContactBadge");
    }

    private boolean isEditTextView(String tag) {
        return tag.equals(SdkConstants.EDIT_TEXT) 
                || tag.equals("TextInputEditText")
                || tag.endsWith(".EditText") 
                || tag.endsWith(".TextInputEditText");
    }

    private void checkImageView(@NonNull XmlContext context, @NonNull Element element) {
        boolean hasContentDesc = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION);
        
        String important = element.getAttributeNS(SdkConstants.ANDROID_URI, "importantForAccessibility");
        boolean isImportantNo = "no".equals(important) || "noHideDescendants".equals(important);

        if (!hasContentDesc && !isImportantNo) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `contentDescription` attribute on image"
            );
        }
    }

    private void checkEditTextView(@NonNull XmlContext context, @NonNull Element element) {
        boolean hasContentDesc = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION);
        boolean hasHint = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_HINT);

        if (hasContentDesc && hasHint) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Do not set both `contentDescription` and `hint` on text fields, as the `hint` will never be shown"
            );
        }
    }
}