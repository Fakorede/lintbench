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

public class AccessibilityDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without contentDescription",
            "Non-textual widgets like ImageViews and ImageButtons should use the "
                    + "contentDescription attribute to specify a textual description of the widget "
                    + "such that screen readers and other accessibility tools can adequately describe "
                    + "the user interface.\n\n"
                    + "Note that elements in application screens that are purely decorative and do not "
                    + "provide any content or enable a user action should not have accessibility content "
                    + "descriptions. In this case, set their descriptions to @null. If your app's "
                    + "minSdkVersion is 16 or higher, you can instead set these graphical elements' "
                    + "android:importantForAccessibility attributes to no.\n\n"
                    + "Note that for text fields, you should not set both the hint and the "
                    + "contentDescription attributes since the hint will never be shown. Just set the hint.",
            Category.ACCESSIBILITY,
            5,
            Severity.WARNING,
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("ImageView", "ImageButton", "EditText", "TextView");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("contentDescription", "hint");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("ImageView".equals(tag) || "ImageButton".equals(tag)) {
            String cd = element.getAttribute("contentDescription");
            if (cd.isEmpty()) {
                cd = element.getAttribute("android:contentDescription");
            }

            if (cd.isEmpty()) {
                String important = element.getAttribute("importantForAccessibility");
                if (important.isEmpty()) {
                    important = element.getAttribute("android:importantForAccessibility");
                }
                if (!"no".equals(important)) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Missing contentDescription attribute on image");
                }
            }
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if ("contentDescription".equals(name)) {
            Element owner = attribute.getOwnerElement();
            String tag = owner.getTagName();
            if ("EditText".equals(tag) || "TextView".equals(tag)) {
                boolean hasHint = owner.hasAttribute("hint") || owner.hasAttribute("android:hint");
                if (hasHint) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "Do not set both hint and contentDescription on a text field; the hint will never be shown");
                }
            }
        }
    }
}