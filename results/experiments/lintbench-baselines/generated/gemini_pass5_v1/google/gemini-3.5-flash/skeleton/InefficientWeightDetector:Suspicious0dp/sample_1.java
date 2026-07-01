package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                            + "when sizing the children. However, if you use 0dp for the opposite dimension, "
                            + "the view will be invisible. This can happen if you change the orientation of a "
                            + "layout without also flipping the `0dp` dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        if (!parentTag.equals("LinearLayout")
                && !parentTag.endsWith(".LinearLayout")
                && !parentTag.equals("RadioGroup")
                && !parentTag.endsWith(".RadioGroup")
                && !parentTag.endsWith("LinearLayoutCompat")) {
            return;
        }

        if (!element.hasAttributeNS(ANDROID_URI, "layout_weight")) {
            return;
        }

        String orientation = parent.getAttributeNS(ANDROID_URI, "orientation");
        boolean isVertical = "vertical".equals(orientation);

        String suspiciousAttr = isVertical ? "layout_width" : "layout_height";
        Attr attribute = element.getAttributeNodeNS(ANDROID_URI, suspiciousAttr);
        if (attribute != null && isZero(attribute.getValue())) {
            String message = String.format(
                    "Suspicious 0dp dimension; this view will be invisible because the parent layout is %s and the %s is 0dp",
                    isVertical ? "vertical" : "horizontal",
                    suspiciousAttr);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private static boolean isZero(String value) {
        if (value == null) {
            return false;
        }
        value = value.trim();
        if (value.equals("0")) {
            return true;
        }
        if (value.startsWith("0")) {
            String unit = value.substring(1).trim();
            return unit.equals("dp")
                    || unit.equals("dip")
                    || unit.equals("px")
                    || unit.equals("sp")
                    || unit.equals("in")
                    || unit.equals("mm")
                    || unit.equals("pt");
        }
        return false;
    }
}