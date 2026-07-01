package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime. It's possible to specify these widths via styles as well. " +
            "GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if (isHelperTag(tagName)) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parent = (Element) parentNode;
            String parentTagName = parent.getTagName();
            if (isGridLayout(parentTagName)) {
                return;
            }
        }

        if (element.hasAttribute("style")) {
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

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    message
            );
        }
    }

    private boolean isHelperTag(String tagName) {
        switch (tagName) {
            case "merge":
            case "include":
            case "fragment":
            case "requestFocus":
            case "tag":
            case "layout":
            case "data":
            case "variable":
            case "import":
            case "transition":
            case "fade":
            case "slide":
            case "explode":
            case "changeBounds":
            case "changeTransform":
            case "changeClipBounds":
            case "changeImageTransform":
            case "transitionSet":
            case "targets":
            case "target":
            case "arcMotion":
            case "pathMotion":
            case "patternPathMotion":
            case "item":
            case "group":
                return true;
            default:
                return false;
        }
    }

    private boolean isGridLayout(String tagName) {
        return "GridLayout".equals(tagName)
                || "android.widget.GridLayout".equals(tagName)
                || "androidx.gridlayout.widget.GridLayout".equals(tagName)
                || "android.support.v7.widget.GridLayout".equals(tagName);
    }
}