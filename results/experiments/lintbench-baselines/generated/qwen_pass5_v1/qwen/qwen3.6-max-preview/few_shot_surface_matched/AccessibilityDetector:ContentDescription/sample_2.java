package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.List;

public class AccessibilityDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_CONTENT_DESCRIPTION = "contentDescription";
    private static final String ATTR_HINT = "hint";
    private static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";
    private static final String VALUE_NO = "no";
    private static final String VALUE_NULL = "@null";

    private static final List<String> IMAGE_WIDGETS = Arrays.asList(
            "ImageView", "ImageButton",
            "android.widget.ImageView", "android.widget.ImageButton"
    );

    private static final List<String> TEXT_WIDGETS = Arrays.asList(
            "EditText", "TextView",
            "android.widget.EditText", "android.widget.TextView"
    );

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
                    + "`contentDescription` attributes since the hint will never be shown. Just "
                    + "set the `hint`.",
            Category.ACCESSIBILITY,
            5,
            Severity.WARNING,
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return IMAGE_WIDGETS;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_CONTENT_DESCRIPTION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (IMAGE_WIDGETS.contains(tag)) {
            Attr contentDesc = element.getAttributeNodeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);
            if (contentDesc == null) {
                Attr important = element.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
                if (important == null || !VALUE_NO.equals(important.getValue())) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Image without contentDescription");
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String attrName = attribute.getLocalName() != null ? attribute.getLocalName() : attribute.getName();
        if (ATTR_CONTENT_DESCRIPTION.equals(attrName)) {
            Element element = attribute.getOwnerElement();
            String tag = element.getTagName();
            if (TEXT_WIDGETS.contains(tag)) {
                Attr hint = element.getAttributeNodeNS(ANDROID_URI, ATTR_HINT);
                if (hint != null) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "Do not set both hint and contentDescription on text fields; "
                                    + "the hint will never be shown. Just set the hint.");
                }
            }
        }
    }
}