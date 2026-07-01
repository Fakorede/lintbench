package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue NESTED_WEIGHTS = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with " +
            "non-zero weights is nested inside another `LinearLayout` with non-zero weights, " +
            "then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this LinearLayout has a non-zero layout_weight itself
        if (!hasNonZeroWeight(element)) {
            return;
        }

        // Walk up the ancestor chain to see if any ancestor LinearLayout also has non-zero weights
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getLocalName()) ||
                LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                if (hasNonZeroWeight(parentElement)) {
                    // Found a nested weight situation
                    Attr weightAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_LAYOUT_WEIGHT);
                    if (weightAttr == null) {
                        weightAttr = element.getAttributeNode("android:" + ATTR_LAYOUT_WEIGHT);
                    }
                    if (weightAttr != null) {
                        context.report(
                                NESTED_WEIGHTS,
                                weightAttr,
                                context.getLocation(weightAttr),
                                "Nested weights are bad for performance"
                        );
                    } else {
                        context.report(
                                NESTED_WEIGHTS,
                                element,
                                context.getLocation(element),
                                "Nested weights are bad for performance"
                        );
                    }
                    return;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private boolean hasNonZeroWeight(Element element) {
        // Check android:layout_weight attribute
        String weight = element.getAttributeNS(ANDROID_NS, ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            // Try without namespace (some parsers)
            NamedNodeMap attrs = element.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                String localName = attr.getLocalName();
                if (localName == null) {
                    localName = attr.getNodeName();
                }
                if ("layout_weight".equals(localName) || "android:layout_weight".equals(localName)) {
                    weight = attr.getNodeValue();
                    break;
                }
            }
        }
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            float value = Float.parseFloat(weight);
            return value != 0f;
        } catch (NumberFormatException e) {
            // If we can't parse it, assume it might be non-zero
            return true;
        }
    }
}