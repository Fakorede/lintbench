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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String ATTR_CONTENT_DESCRIPTION = "contentDescription";
    private static final String ATTR_HINT = "hint";
    private static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Image without `contentDescription`",
                    "Non-textual widgets like ImageViews and ImageButtons should use the "
                            + "`contentDescription` attribute to specify a textual description of the "
                            + "widget such that screen readers and other accessibility tools can "
                            + "adequately describe the user interface.\n\n"
                            + "Note that elements in application screens that are purely decorative "
                            + "and do not provide any content or enable a user action should not have "
                            + "accessibility content descriptions. In this case, set their descriptions "
                            + "to `@null`. If your app's minSdkVersion is 16 or higher, you can instead "
                            + "set these graphical elements' `android:importantForAccessibility` "
                            + "attributes to `no`.\n\n"
                            + "Note that for text fields, you should not set both the `hint` and the "
                            + "`contentDescription` attributes since the hint will never be shown. Just "
                            + "set the `hint`.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("ImageView", "ImageButton");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_CONTENT_DESCRIPTION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 16) {
            String important = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
            if ("no".equals(important) || "noHideDescendants".equals(important)) {
                return;
            }
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `contentDescription` attribute on image");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tag = element.getTagName();

        if (!isTextField(tag)) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.equals("@null")) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Do not set both `contentDescription` and `hint` on text fields; just set `hint`");
        }
    }

    private static boolean isTextField(String tag) {
        return tag.endsWith("EditText")
                || tag.equals("AutoCompleteTextView")
                || tag.equals("MultiAutoCompleteTextView");
    }
}