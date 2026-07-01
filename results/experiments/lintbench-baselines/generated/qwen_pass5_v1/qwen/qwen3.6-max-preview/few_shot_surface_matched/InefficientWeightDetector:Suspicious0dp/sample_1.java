package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal LinearLayout with weights is a useful "
                    + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                    + "when sizing the children. However, if you use 0dp for the opposite dimension, "
                    + "the view will be invisible. This can happen if you change the orientation of a "
                    + "layout without also flipping the 0dp dimension in all the children.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parentElement = (Element) parent;
        if (!LINEAR_LAYOUT.equals(parentElement.getTagName())) {
            return;
        }

        String orientation = parentElement.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        Attr widthAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        Attr heightAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (widthAttr != null) {
            String width = widthAttr.getValue();
            if (("0dp".equals(width) || "0dip".equals(width)) && isVertical) {
                context.report(ISSUE, widthAttr, context.getLocation(widthAttr),
                        "Suspicious size: this will make the view invisible, should be used with layout_weight");
            }
        }

        if (heightAttr != null) {
            String height = heightAttr.getValue();
            if (("0dp".equals(height) || "0dip".equals(height)) && !isVertical) {
                context.report(ISSUE, heightAttr, context.getLocation(heightAttr),
                        "Suspicious size: this will make the view invisible, should be used with layout_weight");
            }
        }
    }
}