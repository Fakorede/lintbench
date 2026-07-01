package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, "
                    + "an exception is thrown at runtime.\n\n"
                    + "It's possible to specify these widths via styles as well. "
                    + "GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, Scope.LAYOUT_RESOURCE_FILES)
    );

    public RequiredAttributeDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (tagName.isEmpty()) {
            return;
        }

        // Determine if the element represents a View
        boolean isView = false;
        char firstChar = tagName.charAt(0);
        if (Character.isUpperCase(firstChar)) {
            isView = true;
        } else if (tagName.indexOf('.') != -1) {
            isView = true;
        } else if (tagName.equals("view") || tagName.equals("fragment")) {
            isView = true;
        }

        if (!isView) {
            return;
        }

        // If it has a style attribute, it might define width and height
        if (element.hasAttribute("style")) {
            return;
        }

        // GridLayout children do not require width/height
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (parentTag.equals("GridLayout") || parentTag.endsWith(".GridLayout")) {
                return;
            }
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth && !hasHeight) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The view is missing both layout_width and layout_height attributes");
        } else if (!hasWidth) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The view is missing layout_width attribute");
        } else if (!hasHeight) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The view is missing layout_height attribute");
        }
    }
}