package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AccessibilityDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Image without `contentDescription`",
                    "Non-textual widgets like ImageViews and ImageButtons should use the `contentDescription` "
                            + "attribute to specify a textual description of the widget such that screen readers "
                            + "and other accessibility tools can adequately describe the user interface.\n\n"
                            + "Note that elements in application screens that are purely decorative and do not "
                            + "provide any content or enable a user action should not have accessibility content "
                            + "descriptions. In this case, set their descriptions to `@null`. If your app's "
                            + "minSdkVersion is 16 or higher, you can instead set these graphical elements' "
                            + "`android:importantForAccessibility` attributes to `no`.\n\n"
                            + "Note that for text fields, you should not set both the `hint` and the "
                            + "`contentDescription` attributes since the hint will never be shown. Just set the `hint`.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_CONTENT_DESCRIPTION = "contentDescription";
    private static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";
    private static final String ATTR_HINT = "hint";
    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_HIDE_DESCENDANTS = "noHideDescendants";

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("*");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (isImageElement(tagName)) {
            checkImage(context, element);
        } else if (isTextFieldElement(tagName)) {
            checkTextField(context, element);
        }
    }

    private boolean isImageElement(String tagName) {
        return tagName.equals("ImageView")
                || tagName.equals("ImageButton")
                || tagName.equals("QuickContactBadge")
                || tagName.endsWith(".ImageView")
                || tagName.endsWith(".ImageButton")
                || tagName.endsWith(".QuickContactBadge");
    }

    private boolean isTextFieldElement(String tagName) {
        return tagName.equals("EditText")
                || tagName.equals("AutoCompleteTextView")
                || tagName.equals("MultiAutoCompleteTextView")
                || tagName.equals("TextInputEditText")
                || tagName.endsWith(".EditText")
                || tagName.endsWith(".AutoCompleteTextView")
                || tagName.endsWith(".MultiAutoCompleteTextView")
                || tagName.endsWith(".TextInputEditText");
    }

    private void checkImage(@NonNull XmlContext context, @NonNull Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY)) {
                String important = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
                if (VALUE_NO.equals(important) || VALUE_NO_HIDE_DESCENDANTS.equals(important)) {
                    return;
                }
            }
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "[Accessibility] Missing `contentDescription` attribute on image");
        }
    }

    private void checkTextField(@NonNull XmlContext context, @NonNull Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
                && element.hasAttributeNS(ANDROID_URI, ATTR_HINT)) {
            Attr contentDescAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);
            context.report(
                    ISSUE,
                    contentDescAttr,
                    context.getLocation(contentDescAttr),
                    "Do not set both `contentDescription` and `hint` on text fields, "
                            + "since the hint will never be shown. Just set the `hint`.");
        }
    }
}