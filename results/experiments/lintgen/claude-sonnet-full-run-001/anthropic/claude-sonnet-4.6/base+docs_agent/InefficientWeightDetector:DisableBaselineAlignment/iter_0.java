package com.android.tools.lint.checks;

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
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between " +
            "nested layouts, the baseline alignment property should be turned off to " +
            "make the layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if baselineAligned is already set to false
        Attr baselineAligned = element.getAttributeNodeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (baselineAligned != null && baselineAligned.getValue().equals("false")) {
            // Already disabled, no issue
            return;
        }

        // Check if any direct children have a layout_weight attribute and are themselves layouts
        NodeList children = element.getChildNodes();
        boolean hasWeightedChildLayout = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            // Check if the child has layout_weight
            Attr weightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weightAttr != null) {
                // Check if the child is a layout (ViewGroup) - i.e., it has children or is a known layout
                String tagName = childElement.getTagName();
                if (isLayout(tagName)) {
                    hasWeightedChildLayout = true;
                    break;
                }
            }
        }

        if (hasWeightedChildLayout) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance"
            );
        }
    }

    private static boolean isLayout(String tagName) {
        // Check common layout/ViewGroup names
        if (tagName.endsWith("Layout")) {
            return true;
        }
        switch (tagName) {
            case "LinearLayout":
            case "RelativeLayout":
            case "FrameLayout":
            case "TableLayout":
            case "TableRow":
            case "GridLayout":
            case "ConstraintLayout":
            case "ScrollView":
            case "HorizontalScrollView":
            case "ListView":
            case "GridView":
            case "ViewGroup":
            case "merge":
            case "include":
                return true;
            default:
                // If the tag contains a dot (fully qualified), assume it could be a layout
                return tagName.contains(".");
        }
    }
}