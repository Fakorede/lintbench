package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.TAG_LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                    + "trick to ensure that only the weights (and not the intrinsic sizes) are "
                    + "used when sizing the children.\n\n"
                    + "However, if you use 0dp for the opposite dimension, the view will be "
                    + "invisible. This can happen if you change the orientation of a layout "
                    + "without also flipping the `0dp` dimension in all the children.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        final boolean isHorizontal;
        if (orientation == null || orientation.isEmpty()
                || VALUE_HORIZONTAL.equals(orientation)) {
            isHorizontal = true;
        } else if (VALUE_VERTICAL.equals(orientation)) {
            isHorizontal = false;
        } else {
            // Could be a resource reference; skip to avoid false positives.
            return;
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String attrName = isHorizontal ? ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;
                Attr attr = childElement.getAttributeNodeNS(ANDROID_URI, attrName);
                if (attr != null && isZeroDp(attr.getValue())) {
                    String message = isHorizontal
                            ? "Suspicious 0dp dimension: in a horizontal `LinearLayout`, "
                                    + "weights affect width; a height of `0dp` makes this view "
                                    + "invisible"
                            : "Suspicious 0dp dimension: in a vertical `LinearLayout`, "
                                    + "weights affect height; a width of `0dp` makes this view "
                                    + "invisible";
                    context.report(ISSUE, attr, context.getLocation(attr), message);
                }
            }
            child = child.getNextSibling();
        }
    }

    private static boolean isZeroDp(@NonNull String value) {
        return "0dp".equals(value) || "0dip".equals(value);
    }
}