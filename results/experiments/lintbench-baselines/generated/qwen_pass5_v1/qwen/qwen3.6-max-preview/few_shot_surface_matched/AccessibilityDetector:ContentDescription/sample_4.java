package com.android.tools.lint.checks;

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
import java.util.Collections;

public class AccessibilityDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
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
                    Category.ACCESSIBILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            AccessibilityDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("ImageView", "ImageButton");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("contentDescription");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr contentDesc = element.getAttributeNodeNS(ANDROID_NS, "contentDescription");
        if (contentDesc != null) {
            return;
        }

        Attr important = element.getAttributeNodeNS(ANDROID_NS, "importantForAccessibility");
        if (important != null && "no".equals(important.getValue())) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `contentDescription` attribute on image");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element owner = attribute.getOwnerElement();
        if ("EditText".equals(owner.getTagName())) {
            Attr hint = owner.getAttributeNodeNS(ANDROID_NS, "hint");
            if (hint != null) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Do not set both `contentDescription` and `hint` on a text field");
            }
        }
    }
}