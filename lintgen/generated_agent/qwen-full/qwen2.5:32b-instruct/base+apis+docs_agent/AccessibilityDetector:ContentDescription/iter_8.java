package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AccessibilityDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE_IMAGE_WITHOUT_CONTENT_DESCRIPTION = Issue.create(
            "ImageWithoutContentDescription",
            "Non-textual widgets like ImageViews and ImageButtons should use the `contentDescription` attribute to specify a textual description of the widget.",
            "Specify a content description for non-textual widgets or set it to `@null` if purely decorative. For text fields, avoid setting both hint and contentDescription.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("ImageView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getTagName().equals("ImageView") || element.getTagName().equals("ImageButton")) {
            Attr contentDescriptionAttr = element.getAttributeNode("android:contentDescription");
            if (contentDescriptionAttr == null || contentDescriptionAttr.getValue().trim().isEmpty()) {
                context.report(ISSUE_IMAGE_WITHOUT_CONTENT_DESCRIPTION, element,
                        context.getLocation(element),
                        "ImageView or ImageButton should have a `contentDescription` attribute.");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }
}