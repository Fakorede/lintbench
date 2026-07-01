package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.TAG_LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal LinearLayout with weights is a useful trick "
                    + "to ensure that only the weights (and not the intrinsic sizes) are used "
                    + "when sizing the children.\n\n"
                    + "However, if you use 0dp for the opposite dimension, the view will be "
                    + "invisible. This can happen if you change the orientation of a layout "
                    + "without also flipping the 0dp dimension in all the children.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean vertical = VALUE_VERTICAL.equals(orientation);

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            String weightValue = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            float weight = 0f;
            if (!weightValue.isEmpty()) {
                try {
                    weight = Float.parseFloat(weightValue);
                } catch (NumberFormatException ignored) {
                }
            }
            boolean hasPositiveWeight = weight > 0f;

            String width = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            String height = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

            if (vertical) {
                if (isZeroDimension(width)) {
                    reportSuspiciousDimension(context, childElement, ATTR_LAYOUT_WIDTH, width);
                }
                if (isZeroDimension(height) && !hasPositiveWeight) {
                    reportSuspiciousDimension(context, childElement, ATTR_LAYOUT_HEIGHT, height);
                }
            } else {
                if (isZeroDimension(width) && !hasPositiveWeight) {
                    reportSuspiciousDimension(context, childElement, ATTR_LAYOUT_WIDTH, width);
                }
                if (isZeroDimension(height)) {
                    reportSuspiciousDimension(context, childElement, ATTR_LAYOUT_HEIGHT, height);
                }
            }
        }
    }

    private static void reportSuspiciousDimension(
            XmlContext context, Element element, String attributeName, String attributeValue) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, attributeName);
        if (attr == null) {
            return;
        }
        String message = String.format(
                "Suspicious 0dp dimension: the view may be invisible because `%1$s` is set to `%2$s`",
                attributeName, attributeValue);
        context.report(ISSUE, attr, context.getLocation(attr), message);
    }

    private static boolean isZeroDimension(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String s = value.trim();
        int length = s.length();
        int i = 0;
        boolean sawDot = false;
        while (i < length) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                i++;
            } else if (c == '.' && !sawDot) {
                sawDot = true;
                i++;
            } else {
                break;
            }
        }
        if (i == 0) {
            return false;
        }
        String number = s.substring(0, i);
        try {
            if (Float.parseFloat(number) != 0f) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        String unit = s.substring(i).trim();
        return unit.isEmpty()
                || unit.equalsIgnoreCase("dp")
                || unit.equalsIgnoreCase("dip")
                || unit.equalsIgnoreCase("sp")
                || unit.equalsIgnoreCase("px")
                || unit.equalsIgnoreCase("pt")
                || unit.equalsIgnoreCase("mm")
                || unit.equalsIgnoreCase("in");
    }
}