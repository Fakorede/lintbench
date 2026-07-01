package com.android.tools.lint.checks;

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

    public static final Issue ISSUE =
            Issue.create(
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
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttribute("android:orientation");
        boolean isHorizontal = !"vertical".equals(orientation);

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            Attr weightAttr = childElement.getAttributeNode("android:layout_weight");
            if (weightAttr == null) {
                continue;
            }

            String dimAttrName = isHorizontal ? "android:layout_height" : "android:layout_width";
            Attr dimAttr = childElement.getAttributeNode(dimAttrName);
            if (dimAttr != null) {
                String value = dimAttr.getValue();
                if (value != null && (value.equals("0dp") || value.equals("0dip") || value.equals("0px"))) {
                    context.report(
                            ISSUE,
                            dimAttr,
                            context.getLocation(dimAttr),
                            "Suspicious dimension: " + dimAttrName + " should not be 0dp in a "
                                    + (isHorizontal ? "horizontal" : "vertical") + " LinearLayout");
                }
            }
        }
    }
}