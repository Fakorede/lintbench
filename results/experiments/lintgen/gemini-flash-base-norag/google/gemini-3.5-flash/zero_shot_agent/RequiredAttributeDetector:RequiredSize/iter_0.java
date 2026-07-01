package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class RequiredAttributeDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime.\n\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special " +
            "case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.LAYOUT_RESOURCE_FILES
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tagName = element.getTagName();
        if (tagName.isEmpty()) {
            return;
        }

        // Skip utility/helper tags (like requestFocus, tag, data binding tags)
        // Standard views start with an uppercase letter, but we must also allow the generic <view> tag.
        char firstChar = tagName.charAt(0);
        if (Character.isLowerCase(firstChar) && !tagName.contains(".") && !tagName.equals("view")) {
            return;
        }

        // Skip if the element has a style attribute, as width/height can be defined there
        if (element.hasAttribute("style")) {
            return;
        }

        // GridLayout children do not require layout_width and layout_height
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parent = (Element) parentNode;
            String parentTag = parent.getTagName();
            if (parentTag.equals("GridLayout") || parentTag.endsWith(".GridLayout")) {
                return;
            }
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth && !hasHeight) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The view is missing both attribute 'layout_width' and 'layout_height'");
        } else if (!hasWidth) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The view is missing attribute 'layout_width'");
        } else if (!hasHeight) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The view is missing attribute 'layout_height'");
        }
    }
}