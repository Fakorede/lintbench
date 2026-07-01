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
import java.util.Arrays;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "ImageView",
                "ImageButton",
                "QuickContactBadge",
                "EditText",
                "TextView",
                "android.support.design.widget.TextInputEditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handled in visitElement
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (tagName.equals("ImageView") || tagName.equals("ImageButton") || tagName.equals("QuickContactBadge")) {
            if (!element.hasAttributeNS(ANDROID_URI, "contentDescription")) {
                String important = element.getAttributeNS(ANDROID_URI, "importantForAccessibility");
                if ("no".equals(important) || "noHideDescendants".equals(important)) {
                    return;
                }
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "[Accessibility] Missing `contentDescription` attribute on image"
                );
            }
        } else {
            // Text fields or views that might have both hint and contentDescription
            if (element.hasAttributeNS(ANDROID_URI, "contentDescription")
                    && element.hasAttributeNS(ANDROID_URI, "hint")) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Do not set both `contentDescription` and `hint` on text fields, use only `hint`"
                );
            }
        }
    }
}