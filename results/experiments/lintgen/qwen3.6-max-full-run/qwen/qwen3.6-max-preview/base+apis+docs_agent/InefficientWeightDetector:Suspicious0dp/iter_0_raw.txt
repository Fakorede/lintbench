package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;
import static com.android.SdkConstants.*;

public class InefficientWeightDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "Suspicious0dp",
        "Suspicious 0dp dimension",
        "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful " +
        "trick to ensure that only the weights (and not the intrinsic sizes) are used " +
        "when sizing the children.\n\n" +
        "However, if you use 0dp for the opposite dimension, the view will be invisible. " +
        "This can happen if you change the orientation of a layout without also flipping " +
        "the `0dp` dimension in all the children.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isHorizontal = orientation == null || orientation.equals(VALUE_HORIZONTAL);
        String suspiciousAttr = isHorizontal ? ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;

        for (Element child : LintUtils.getChildren(element)) {
            Attr attr = child.getAttributeNodeNS(ANDROID_URI, suspiciousAttr);
            if (attr != null) {
                String value = attr.getValue();
                if ("0dp".equals(value) || "0dip".equals(value)) {
                    context.report(ISSUE, child, context.getLocation(attr),
                        "Suspicious size: this will make the view invisible, should be used with layout_weight");
                }
            }
        }
    }
}