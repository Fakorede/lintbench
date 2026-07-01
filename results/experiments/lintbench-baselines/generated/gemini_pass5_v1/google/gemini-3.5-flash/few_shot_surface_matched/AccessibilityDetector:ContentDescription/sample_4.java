package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class AccessibilityDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without contentDescription",
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
            new Implementation(AccessibilityDetector.class, Scope.LAYOUT_RESOURCE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "ImageView",
                "ImageButton",
                "QuickContactBadge"
        );
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("contentDescription");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", "contentDescription")) {
            return;
        }
        String important = element.getAttributeNS("http://schemas.android.com/apk/res/android", "importantForAccessibility");
        if ("no".equals(important) || "noHideDescendants".equals(important)) {
            return;
        }
        context.report(ISSUE, element, context.getNameLocation(element),
                "Missing `contentDescription` attribute on image");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", "hint")) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Do not set both `contentDescription` and `hint` on the same text field; the `hint` will never be shown");
        }
    }
}