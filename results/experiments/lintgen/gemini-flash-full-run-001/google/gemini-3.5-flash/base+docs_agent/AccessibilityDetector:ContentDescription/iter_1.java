package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Element;

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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        String simpleName = tagName.substring(tagName.lastIndexOf('.') + 1);

        if (simpleName.equals("ImageView") || 
                simpleName.equals("ImageButton") || 
                simpleName.equals("QuickContactBadge") ||
                simpleName.equals("AppCompatImageView") ||
                simpleName.equals("AppCompatImageButton")) {
            
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION)) {
                String important = element.getAttributeNS(SdkConstants.ANDROID_URI, "importantForAccessibility");
                if (!"no".equals(important) && !"noHideDescendants".equals(important)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "[Accessibility] Missing `contentDescription` attribute on image"
                    );
                }
            }
        } else if (simpleName.equals("EditText") || 
                simpleName.equals("AutoCompleteTextView") || 
                simpleName.equals("MultiAutoCompleteTextView") ||
                simpleName.equals("TextInputEditText") ||
                simpleName.equals("AppCompatEditText") ||
                simpleName.equals("AppCompatAutoCompleteTextView")) {
            
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "hint") &&
                    element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_CONTENT_DESCRIPTION)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Do not set both `android:hint` and `android:contentDescription` on a text field; use only `android:hint` instead"
                );
            }
        }
    }
}