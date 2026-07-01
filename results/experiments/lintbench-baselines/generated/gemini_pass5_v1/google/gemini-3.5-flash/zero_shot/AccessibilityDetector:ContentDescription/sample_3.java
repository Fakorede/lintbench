package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import com.android.SdkConstants;

public class AccessibilityDetector extends LayoutDetector {

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
        "`contentDescription` attributes since the hint will never be shown. " +
        "Just set the `hint`.",
        Category.ACCESSIBILITY,
        3,
        Severity.WARNING,
        new Implementation(
            AccessibilityDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null; // Visit all elements to check custom views as well
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        
        if (tagName.equals("ImageView") || tagName.equals("ImageButton") ||
            tagName.endsWith(".ImageView") || tagName.endsWith(".ImageButton") ||
            tagName.endsWith("ImageView") || tagName.endsWith("ImageButton")) {
            checkImage(context, element);
        }
        
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "hint") && 
            element.hasAttributeNS(SdkConstants.ANDROID_URI, "contentDescription")) {
            checkTextField(context, element);
        }
    }

    private void checkImage(XmlContext context, Element element) {
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "contentDescription")) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "importantForAccessibility")) {
            String important = element.getAttributeNS(SdkConstants.ANDROID_URI, "importantForAccessibility");
            if ("no".equals(important) || "noHideDescendants".equals(important)) {
                return;
            }
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "Missing `contentDescription` attribute on image"
        );
    }

    private void checkTextField(XmlContext context, Element element) {
        Attr contentDescriptionNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "contentDescription");
        if (contentDescriptionNode != null) {
            context.report(
                ISSUE,
                contentDescriptionNode,
                context.getLocation(contentDescriptionNode),
                "Do not set both `contentDescription` and `hint` on text fields, " +
                "since the `hint` will never be shown. Just set the `hint`."
            );
        }
    }
}