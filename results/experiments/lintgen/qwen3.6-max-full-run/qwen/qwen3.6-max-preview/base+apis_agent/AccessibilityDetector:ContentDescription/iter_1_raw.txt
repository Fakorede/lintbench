package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AccessibilityDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "ContentDescription",
        "Image without contentDescription",
        "Non-textual widgets like ImageViews and ImageButtons should use the `contentDescription` attribute to specify a textual description of the widget such that screen readers and other accessibility tools can adequately describe the user interface.\n\n" +
        "Note that elements in application screens that are purely decorative and do not provide any content or enable a user action should not have accessibility content descriptions. In this case, set their descriptions to `@null`. If your app's minSdkVersion is 16 or higher, you can instead set these graphical elements' `android:importantForAccessibility` attributes to `no`.\n\n" +
        "Note that for text fields, you should not set both the `hint` and the `contentDescription` attributes since the hint will never be shown. Just set the hint.",
        Category.A11Y,
        3,
        Severity.WARNING,
        new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Collection<String> IMAGE_TAGS = Arrays.asList(
        "ImageView", "ImageButton",
        "android.widget.ImageView", "android.widget.ImageButton"
    );

    private static final Collection<String> TEXT_TAGS = Arrays.asList(
        "EditText", "TextView",
        "android.widget.EditText", "android.widget.TextView"
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
            "ImageView", "ImageButton", "EditText", "TextView",
            "android.widget.ImageView", "android.widget.ImageButton",
            "android.widget.EditText", "android.widget.TextView"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        boolean isImage = IMAGE_TAGS.contains(tag);
        boolean isText = TEXT_TAGS.contains(tag);

        String contentDesc = element.getAttributeNS(ANDROID_URI, "contentDescription");
        String important = element.getAttributeNS(ANDROID_URI, "importantForAccessibility");
        String hint = element.getAttributeNS(ANDROID_URI, "hint");

        if (isImage) {
            boolean hasContentDesc = contentDesc != null && !contentDesc.isEmpty();
            boolean isNullDesc = "@null".equals(contentDesc);
            boolean isImportantNo = "no".equals(important);
            int minSdk = context.getMainProject().getMinSdkVersion() != null
                ? context.getMainProject().getMinSdkVersion().getApiLevel()
                : 1;

            if (!hasContentDesc && !isNullDesc && !(isImportantNo && minSdk >= 16)) {
                context.report(ISSUE, element, context.getLocation(element),
                    "Image without `contentDescription`");
            }
        }

        if (isText) {
            boolean hasContentDesc = contentDesc != null && !contentDesc.isEmpty() && !"@null".equals(contentDesc);
            boolean hasHint = hint != null && !hint.isEmpty();

            if (hasContentDesc && hasHint) {
                context.report(ISSUE, element, context.getLocation(element),
                    "Do not set both `hint` and `contentDescription` on a text field");
            }
        }
    }
}