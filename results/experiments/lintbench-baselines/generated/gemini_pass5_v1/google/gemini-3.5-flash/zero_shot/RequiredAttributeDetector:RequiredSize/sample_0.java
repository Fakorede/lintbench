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
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime.\n\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special " +
            "case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE)
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
        if (tagName.equals(SdkConstants.VIEW_MERGE) ||
            tagName.equals(SdkConstants.VIEW_INCLUDE) ||
            tagName.equals("layout") ||
            tagName.equals("data") ||
            tagName.equals("variable") ||
            tagName.equals("import") ||
            tagName.equals("requestFocus") ||
            tagName.equals("tag")) {
            return;
        }

        // Check if nested inside a GridLayout
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (parentTag.equals(SdkConstants.GRID_LAYOUT) ||
                parentTag.equals("android.support.v7.widget.GridLayout") ||
                parentTag.equals("androidx.gridlayout.widget.GridLayout")) {
                return;
            }
        }

        // If a style is defined, it might specify the width/height
        if (element.hasAttribute(SdkConstants.ATTR_STYLE)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            String message;
            if (!hasWidth && !hasHeight) {
                message = "The view is missing both `android:layout_width` and `android:layout_height` attributes";
            } else if (!hasWidth) {
                message = "The view is missing the `android:layout_width` attribute";
            } else {
                message = "The view is missing the `android:layout_height` attribute";
            }
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }
}