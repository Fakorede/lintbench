package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;

public class RequiredAttributeDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime.\n\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special " +
            "case, does not require you to specify a size.",
            Category.CORRECTNESS, 6, Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!(element.getParentNode() instanceof Element)) {
            return;
        }

        String tag = element.getTagName();
        if (isNonViewTag(tag)) {
            return;
        }

        Element parent = (Element) element.getParentNode();
        String parentTag = parent.getTagName();

        if (isGridLayout(parentTag)) {
            return;
        }

        if (element.hasAttribute(SdkConstants.ATTR_STYLE)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth && !hasHeight) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing both layout_width and layout_height attributes");
        } else if (!hasWidth) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing layout_width attribute");
        } else if (!hasHeight) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing layout_height attribute");
        }
    }

    private static boolean isNonViewTag(String tag) {
        return tag.equals(SdkConstants.TAG_MERGE) ||
               tag.equals(SdkConstants.TAG_INCLUDE) ||
               tag.equals(SdkConstants.TAG_REQUEST_FOCUS) ||
               tag.equals(SdkConstants.TAG_TAG);
    }

    private static boolean isGridLayout(String tag) {
        return tag.equals("GridLayout") || tag.equals("androidx.gridlayout.widget.GridLayout");
    }
}