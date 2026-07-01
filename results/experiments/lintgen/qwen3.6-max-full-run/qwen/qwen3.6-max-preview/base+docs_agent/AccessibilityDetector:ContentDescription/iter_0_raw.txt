package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AccessibilityDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without `contentDescription`",
            "Non-textual widgets like ImageViews and ImageButtons should use the " +
            "`contentDescription` attribute to specify a textual description of " +
            "the widget such that screen readers and other accessibility tools " +
            "can adequately describe the user interface.\n\n" +
            "Note that elements in application screens that are purely decorative " +
            "and do not provide any content or enable a user action should not " +
            "have accessibility content descriptions. In this case, set their " +
            "descriptions to `@null`. If your app's minSdkVersion is 16 or higher, " +
            "you can instead set these graphical elements' " +
            "`android:importantForAccessibility` attributes to `no`.\n\n" +
            "Note that for text fields, you should not set both the `hint` and the " +
            "`contentDescription` attributes since the hint will never be shown. Just " +
            "set the hint.",
            Category.ACCESSIBILITY,
            3,
            Severity.WARNING,
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("ImageView", "ImageButton");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String contentDescription = element.getAttributeNS(ANDROID_URI, "contentDescription");
        if (contentDescription != null && !contentDescription.isEmpty()) {
            return;
        }

        String important = element.getAttributeNS(ANDROID_URI, "importantForAccessibility");
        if ("no".equals(important)) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        String message = "Missing `contentDescription` attribute on image";
        if (minSdk >= 16) {
            message += " (or set `android:importantForAccessibility=\"no\"` if decorative)";
        } else {
            message += " (or set `android:contentDescription=\"@null\"` if decorative)";
        }

        context.report(ISSUE, element, context.getLocation(element), message);
    }
}