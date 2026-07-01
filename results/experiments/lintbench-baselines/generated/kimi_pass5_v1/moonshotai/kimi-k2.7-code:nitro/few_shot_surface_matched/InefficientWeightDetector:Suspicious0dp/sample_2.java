package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VALUE_ZERO_DP;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful"
                            + " trick to ensure that only the weights (and not the intrinsic"
                            + " sizes) are used when sizing the children. However, if you use 0dp"
                            + " for the opposite dimension, the view will be invisible. This can"
                            + " happen if you change the orientation of a layout without also"
                            + " flipping the 0dp dimension in all the children.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean horizontal =
                orientation == null
                        || orientation.isEmpty()
                        || VALUE_HORIZONTAL.equals(orientation);
        boolean vertical = VALUE_VERTICAL.equals(orientation);
        if (!horizontal && !vertical) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;

            if (horizontal) {
                String height = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                if (VALUE_ZERO_DP.equals(height)) {
                    Attr attr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                    if (attr != null) {
                        context.report(
                                ISSUE,
                                attr,
                                context.getLocation(attr),
                                "Suspicious 0dp dimension: in a horizontal LinearLayout,"
                                        + " layout_height=\"0dp\" makes this view invisible.");
                    }
                }
            } else {
                String width = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                if (VALUE_ZERO_DP.equals(width)) {
                    Attr attr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                    if (attr != null) {
                        context.report(
                                ISSUE,
                                attr,
                                context.getLocation(attr),
                                "Suspicious 0dp dimension: in a vertical LinearLayout,"
                                        + " layout_width=\"0dp\" makes this view invisible.");
                    }
                }
            }
        }
    }
}