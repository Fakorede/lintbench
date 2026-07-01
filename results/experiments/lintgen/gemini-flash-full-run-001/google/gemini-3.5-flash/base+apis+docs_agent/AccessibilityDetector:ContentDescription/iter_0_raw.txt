package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.IMAGE_BUTTON;
import static com.android.SdkConstants.EDIT_TEXT;

public class AccessibilityDetector extends Detector implements XmlScanner {

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
            3,
            Severity.WARNING,
            new Implementation(
                    AccessibilityDetector.class,
                    Scope.LAYOUT_RESOURCE_FILES
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (tagName.equals(IMAGE_VIEW) || tagName.equals(IMAGE_BUTTON) || tagName.equals("QuickContactBadge")
                || tagName.endsWith(".ImageView") || tagName.endsWith(".ImageButton")) {
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
                String important = element.getAttributeNS(ANDROID_URI, "importantForAccessibility");
                if ("no".equals(important) || "noHideDescendants".equals(important)) {
                    return;
                }
                context.report(ISSUE, element, context.getNameLocation(element),
                        "[Accessibility] Missing `contentDescription` attribute on image");
            }
        } else if (tagName.equals(EDIT_TEXT) || tagName.endsWith(".EditText")
                || tagName.equals("AutoCompleteTextView") || tagName.endsWith(".AutoCompleteTextView")
                || tagName.equals("MultiAutoCompleteTextView") || tagName.endsWith(".MultiAutoCompleteTextView")
                || tagName.equals("TextInputLayout") || tagName.endsWith(".TextInputLayout")) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
                    && element.hasAttributeNS(ANDROID_URI, "hint")) {
                context.report(ISSUE, element, context.getNameLocation(element),
                        "Do not set both `contentDescription` and `hint` on text fields, " +
                        "since the hint will never be shown. Just set the `hint`.");
            }
        }
    }
}