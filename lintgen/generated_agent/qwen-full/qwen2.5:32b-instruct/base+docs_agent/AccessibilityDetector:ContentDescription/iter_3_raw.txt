package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AccessibilityDetector extends LayoutDetector {

    public static final Issue ISSUE_IMAGE_WITHOUT_CONTENT_DESCRIPTION = Issue.create(
            "ImageWithoutContentDescription",
            "ImageView or ImageButton without `contentDescription` attribute",
            "Non-textual widgets like ImageViews and ImageButtons should use the `contentDescription` attribute to specify a textual description of the widget such that screen readers and other accessibility tools can adequately describe the user interface. Elements in application screens that are purely decorative and do not provide any content or enable a user action should not have accessibility content descriptions. In this case, set their descriptions to `@null`. If your app's minSdkVersion is 16 or higher, you can instead set these graphical elements' `android:importantForAccessibility` attributes to `no`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AccessibilityDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("ImageView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (isImageViewOrImageButton(element)) {
            Attr contentDescriptionAttr = getAttribute(context, element, "contentDescription");
            Attr importantForAccessibilityAttr = getAttribute(context, element, "importantForAccessibility");

            if (contentDescriptionAttr == null && importantForAccessibilityAttr == null
                    || (importantForAccessibilityAttr != null && !importantForAccessibilityAttr.getValue().equals("no"))) {
                Location location = context.getLocation(element);
                context.report(ISSUE_IMAGE_WITHOUT_CONTENT_DESCRIPTION, element, location,
                        "ImageView or ImageButton should have a `contentDescription` attribute");
            }
        }
    }

    private Attr getAttribute(@NonNull XmlContext context, @NonNull Element element, String attributeName) {
        return (Attr) element.getAttributeNodeNS(null, attributeName);
    }

    private boolean isImageViewOrImageButton(@NonNull Element element) {
        String tagName = element.getTagName();
        return "ImageView".equals(tagName) || "ImageButton".equals(tagName);
    }
}