package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
            new Implementation(RequiredAttributeDetector.class, Scope.LAYOUT_SCOPE)
    );

    public RequiredAttributeDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        
        // Skip helper tags, fragments, includes, and merges
        if (tag.equals(SdkConstants.VIEW_MERGE) ||
                tag.equals(SdkConstants.VIEW_INCLUDE) ||
                tag.equals(SdkConstants.VIEW_FRAGMENT) ||
                tag.equals("requestFocus") ||
                tag.equals("tag")) {
            return;
        }

        // If a style is applied, we assume width/height might be defined there
        if (element.hasAttribute(SdkConstants.ATTR_STYLE)) {
            return;
        }

        // Children of GridLayout do not require layout_width/layout_height
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            String parentTag = ((Element) parentNode).getTagName();
            if (parentTag.equals(SdkConstants.GRID_LAYOUT) ||
                    parentTag.endsWith(".GridLayout") ||
                    parentTag.equals("android.widget.GridLayout") ||
                    parentTag.equals("androidx.gridlayout.widget.GridLayout")) {
                return;
            }
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            String message;
            if (!hasWidth && !hasHeight) {
                message = "The view is missing required layout_width and layout_height attributes";
            } else if (!hasWidth) {
                message = "The view is missing required layout_width attribute";
            } else {
                message = "The view is missing required layout_height attribute";
            }
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }
}