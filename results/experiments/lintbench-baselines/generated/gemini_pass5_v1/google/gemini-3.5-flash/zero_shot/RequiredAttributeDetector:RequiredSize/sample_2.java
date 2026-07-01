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
            "There is a runtime check for this, so if you fail to specify a size, " +
            "an exception is thrown at runtime.\n\n" +
            "It's possible to specify these widths via styles as well. " +
            "GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, Scope.LAYOUT_ONLY)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if (tagName.equals(SdkConstants.VIEW_MERGE) ||
            tagName.equals(SdkConstants.VIEW_INCLUDE) ||
            tagName.equals("requestFocus") ||
            tagName.equals("tag")) {
            return;
        }

        if (tagName.endsWith("GridLayout")) {
            return;
        }
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            String parentTag = ((Element) parentNode).getTagName();
            if (parentTag.endsWith("GridLayout")) {
                return;
            }
        }

        if (element.hasAttribute(SdkConstants.ATTR_STYLE) || element.hasAttribute("style")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            String missing;
            if (!hasWidth && !hasHeight) {
                missing = "both `layout_width` and `layout_height`";
            } else if (!hasWidth) {
                missing = "`layout_width`";
            } else {
                missing = "`layout_height`";
            }

            String message = String.format("The view is missing %s attributes", missing);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }
}