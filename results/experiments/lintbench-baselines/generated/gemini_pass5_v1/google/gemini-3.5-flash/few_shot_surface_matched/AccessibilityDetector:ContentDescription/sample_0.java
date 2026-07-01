package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AccessibilityDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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
                            + "`contentDescription` attributes since the hint will never be shown. Just "
                            + "set the `hint`.",
                    Category.A11Y,
                    4,
                    Severity.WARNING,
                    new Implementation(AccessibilityDetector.class, Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("ImageView", "ImageButton", "EditText");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("contentDescription", "hint");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("ImageView".equals(tagName) || "ImageButton".equals(tagName)) {
            if (!element.hasAttributeNS(ANDROID_URI, "contentDescription")) {
                String important = element.getAttributeNS(ANDROID_URI, "importantForAccessibility");
                if ("no".equals(important) || "noHideDescendants".equals(important)) {
                    return;
                }
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "[Accessibility] Missing `contentDescription` attribute on image");
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if ("contentDescription".equals(name)) {
            Element element = attribute.getOwnerElement();
            String tagName = element.getTagName();
            if ("EditText".equals(tagName)) {
                if (element.hasAttributeNS(ANDROID_URI, "hint")) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Do not set both `contentDescription` and `hint` on text fields, "
                                    + "since the hint will never be shown. Just set the `hint`.");
                }
            }
        }
    }
}