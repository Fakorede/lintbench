package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal LinearLayout with weights is a useful "
                    + "trick to ensure that only the weights are used when sizing the children. "
                    + "However, using 0dp for the opposite dimension will make the view invisible. "
                    + "This often happens when the orientation of a layout is changed without also "
                    + "flipping the 0dp dimension in the children.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_ORIENTATION);
        boolean isHorizontal = orientation == null || orientation.isEmpty()
                || SdkConstants.VALUE_HORIZONTAL.equals(orientation);

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                child = child.getNextSibling();
                continue;
            }

            Element childElement = (Element) child;

            String weight = childElement.getAttributeNS(SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty() || "0".equals(weight)
                    || "0.0".equals(weight)) {
                child = child.getNextSibling();
                continue;
            }

            if (isHorizontal) {
                Attr attr = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_LAYOUT_HEIGHT);
                if (attr != null && isZeroDp(attr.getValue())) {
                    context.report(ISSUE, context.getValueLocation(attr),
                            "Suspicious 0dp height in a horizontal LinearLayout with "
                                    + "layout_weight");
                }
            } else {
                Attr attr = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_LAYOUT_WIDTH);
                if (attr != null && isZeroDp(attr.getValue())) {
                    context.report(ISSUE, context.getValueLocation(attr),
                            "Suspicious 0dp width in a vertical LinearLayout with "
                                    + "layout_weight");
                }
            }

            child = child.getNextSibling();
        }
    }

    private static boolean isZeroDp(String value) {
        return value != null && (value.equals("0dp") || value.equals("0dip")
                || value.equals("0sp") || value.equals("0pt") || value.equals("0px")
                || value.equals("0in") || value.equals("0mm"));
    }
}