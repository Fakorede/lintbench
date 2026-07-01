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
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AccessibilityDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_CONTENT_DESCRIPTION = "contentDescription";
    private static final String ATTR_HINT = "hint";
    private static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";

    private static final String IMAGE_VIEW = "ImageView";
    private static final String IMAGE_BUTTON = "ImageButton";
    private static final String EDIT_TEXT = "EditText";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Image without `contentDescription`",
                    "Non-textual widgets like ImageViews and ImageButtons should use the `contentDescription` attribute to specify a textual description of the widget such that screen readers and other accessibility tools can adequately describe the user interface.\n\n"
                            + "Note that elements in application screens that are purely decorative and do not provide any content or enable a user action should not have accessibility content descriptions. In this case, set their descriptions to `@null`. If your app's `minSdkVersion` is 16 or higher, you can instead set these graphical elements' `android:importantForAccessibility` attributes to `no`.\n\n"
                            + "Note that for text fields, you should not set both the `hint` and the `contentDescription` attributes since the hint will never be shown. Just set the `hint`.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON, EDIT_TEXT);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_CONTENT_DESCRIPTION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (EDIT_TEXT.equals(element.getTagName())) {
            Attr hint = element.getAttributeNodeNS(ANDROID_URI, ATTR_HINT);
            if (hint != null) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Do not set both `contentDescription` and `hint` on an EditText; the hint will never be shown. Use `hint` only.");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (IMAGE_VIEW.equals(tag) || IMAGE_BUTTON.equals(tag)) {
            if (element.getAttributeNodeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION) != null) {
                return;
            }

            int minSdk = context.getMainProject().getMinSdk();
            if (minSdk >= 16) {
                Attr important =
                        element.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
                if (important != null && "no".equals(important.getValue())) {
                    return;
                }
            }

            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `contentDescription` attribute on image");
        }
    }
}