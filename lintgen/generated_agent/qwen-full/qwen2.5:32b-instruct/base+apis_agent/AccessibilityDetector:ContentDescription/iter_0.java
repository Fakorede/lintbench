package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AccessibilityDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE_IMAGE_WITHOUT_CONTENT_DESCRIPTION = Issue.create(
            "ImageWithoutContentDescription",
            "Non-textual widgets like ImageViews and ImageButtons should use the `contentDescription` attribute to specify a textual description of the widget such that screen readers and other accessibility tools can adequately describe the user interface.",
            "Elements in application screens that are purely decorative and do not provide any content or enable a user action should not have accessibility content descriptions. In this case, set their descriptions to `@null`. If your app's minSdkVersion is 16 or higher, you can instead set these graphical elements' `android:importantForAccessibility` attributes to `no`.",
            Category.ACCESSIBILITY,
            5,
            Severity.WARNING,
            new Implementation(AccessibilityDetector.class, true)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("ImageView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getNodeName().equals("ImageView") || element.getNodeName().equals("ImageButton")) {
            Attr contentDescriptionAttr = element.getAttributeNodeNS(null, "contentDescription");
            if (contentDescriptionAttr == null || contentDescriptionAttr.getValue().trim().isEmpty()) {
                context.report(ISSUE_IMAGE_WITHOUT_CONTENT_DESCRIPTION, element,
                        context.getLocation(element), "ImageView or ImageButton should have a `contentDescription` attribute.");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}