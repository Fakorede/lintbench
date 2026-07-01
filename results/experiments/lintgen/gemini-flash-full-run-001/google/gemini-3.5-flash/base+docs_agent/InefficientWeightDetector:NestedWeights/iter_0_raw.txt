package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
                    EnumSet.of(Scope.LAYOUT)
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        List<Element> linearLayouts = new ArrayList<>();
        collectLinearLayouts(document.getDocumentElement(), linearLayouts);

        Map<Element, Boolean> hasWeightsMap = new HashMap<>();
        for (Element layout : linearLayouts) {
            hasWeightsMap.put(layout, hasDirectWeightChild(layout));
        }

        for (Element layout : linearLayouts) {
            if (Boolean.TRUE.equals(hasWeightsMap.get(layout))) {
                Element ancestor = getAncestorLinearLayout(layout);
                while (ancestor != null) {
                    if (Boolean.TRUE.equals(hasWeightsMap.get(ancestor))) {
                        context.report(
                                NESTED_WEIGHTS,
                                layout,
                                context.getNameLocation(layout),
                                "Nested layout weights can extremely slow down rendering"
                        );
                        break;
                    }
                    ancestor = getAncestorLinearLayout(ancestor);
                }
            }
        }
    }

    private void collectLinearLayouts(@Nullable Node node, @NonNull List<Element> list) {
        if (node instanceof Element) {
            Element element = (Element) node;
            String tagName = element.getTagName();
            if (tagName.equals(SdkConstants.LINEAR_LAYOUT) || tagName.endsWith(".LinearLayout")) {
                list.add(element);
            }
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                collectLinearLayouts(children.item(i), list);
            }
        }
    }

    private boolean hasDirectWeightChild(@NonNull Element layout) {
        NodeList children = layout.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element childElement = (Element) child;
                if (childElement.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    String weightStr = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
                    if (isNonZeroWeight(weightStr)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNonZeroWeight(@Nullable String weightStr) {
        if (weightStr == null || weightStr.trim().isEmpty()) {
            return false;
        }
        try {
            double value = Double.parseDouble(weightStr.trim());
            return value != 0.0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    @Nullable
    private Element getAncestorLinearLayout(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            String tagName = parentElement.getTagName();
            if (tagName.equals(SdkConstants.LINEAR_LAYOUT) || tagName.endsWith(".LinearLayout")) {
                return parentElement;
            }
            parent = parent.getParentNode();
        }
        return null;
    }
}