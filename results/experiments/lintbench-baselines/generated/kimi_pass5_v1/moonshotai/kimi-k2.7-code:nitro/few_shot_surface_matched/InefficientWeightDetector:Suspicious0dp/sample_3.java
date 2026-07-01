package com.android.tools.lint.checks;

import com.android.tools.lint.checks.LayoutDetector;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful"
                            + " trick to ensure that only the weights (and not the intrinsic"
                            + " sizes) are used when sizing the children.\n\n"
                            + "However, if you use 0dp for the opposite dimension, the view will"
                            + " be invisible. This can happen if you change the orientation of a"
                            + " layout without also flipping the 0dp dimension in all the"
                            + " children.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_ORIENTATION = "android:orientation";
    private static final String ATTR_LAYOUT_WIDTH = "android:layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "android:layout_height";
    private static final String ATTR_LAYOUT_WEIGHT = "android:layout_weight";
    private static final String ORIENTATION_HORIZONTAL = "horizontal";
    private static final String ORIENTATION_VERTICAL = "vertical";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttribute(ATTR_ORIENTATION);
        if (orientation == null || orientation.isEmpty()) {
            orientation = ORIENTATION_HORIZONTAL;
        }

        boolean horizontal = ORIENTATION_HORIZONTAL.equals(orientation);

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                Attr weight = childElement.getAttributeNode(ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.getValue().isEmpty()) {
                    if (horizontal) {
                        Attr height = childElement.getAttributeNode(ATTR_LAYOUT_HEIGHT);
                        if (height != null && isZeroDp(height.getValue())) {
                            context.report(
                                    ISSUE,
                                    height,
                                    context.getLocation(height),
                                    "Suspicious 0dp value: using 0dp for the height of a child in"
                                            + " a horizontal LinearLayout with a weight will make"
                                            + " the view invisible");
                        }
                    } else {
                        Attr width = childElement.getAttributeNode(ATTR_LAYOUT_WIDTH);
                        if (width != null && isZeroDp(width.getValue())) {
                            context.report(
                                    ISSUE,
                                    width,
                                    context.getLocation(width),
                                    "Suspicious 0dp value: using 0dp for the width of a child in"
                                            + " a vertical LinearLayout with a weight will make"
                                            + " the view invisible");
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    private static boolean isZeroDp(String value) {
        return value != null
                && (value.equals("0dp")
                        || value.equals("0dip")
                        || value.equals("0px")
                        || value.equals("0sp")
                        || value.equals("0pt")
                        || value.equals("0in")
                        || value.equals("0mm"));
    }
}