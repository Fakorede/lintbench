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
                    "Non-textual widgets like ImageViews and ImageButtons should use the "
                            + "`contentDescription` attribute to specify a textual description of "
                            + "the widget such that screen readers and other accessibility tools "
                            + "can adequately describe the user interface.\n\n"
                            + "Note that elements in application screens that are purely decorative "
                            + "and do not provide any content or enable a user action should not "
                            + "have accessibility content descriptions. In this case, set their "
                            + "descriptions to `@null`. If your app's minSdkVersion is 16 or higher, "
                            + "you can instead set these graphical elements' "
                            + "`android:importantForAccessibility` attributes to `no`.\n\n"
                            + "Note that for text fields, you should not set both the `hint` and the "
                            + "`contentDescription` attributes since the hint will never be shown. "
                            + "Just set the `hint`.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                "ImageView",
                "ImageButton",
                "QuickContactBadge",
                "EditText",
                "androidx.appcompat.widget.AppCompatImageView",
                "androidx.appcompat.widget.AppCompatImageButton"
        );
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Not used
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (tag.contains("EditText")) {
            boolean hasHint = element.hasAttributeNS("http://schemas.android.com/apk/res/android", "hint");
            boolean hasContentDesc = element.hasAttributeNS("http://schemas.android.com/apk/res/android", "contentDescription");
            if (hasHint && hasContentDesc) {
                Attr attribute = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "contentDescription");
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(attribute),
                        "Do not set both `android:hint` and `android:contentDescription` on a text field"
                );
            }
        } else {
            boolean hasContentDesc = element.hasAttributeNS("http://schemas.android.com/apk/res/android", "contentDescription");
            if (!hasContentDesc) {
                String important = element.getAttributeNS("http://schemas.android.com/apk/res/android", "importantForAccessibility");
                if ("no".equals(important) || "noHideDescendants".equals(important)) {
                    return;
                }
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Missing `android:contentDescription` attribute on image"
                );
            }
        }
    }
}