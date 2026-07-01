package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a `LinearLayout` is used to distribute the space proportionally between "
                            + "nested layouts, the baseline alignment property should be turned off to "
                            + "make the layout computation faster.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // If baselineAligned is already set to false, nothing to warn about
        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (baselineAligned != null && baselineAligned.equals("false")) {
            return;
        }

        // Check if the LinearLayout is vertical - baseline alignment only matters for horizontal
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        // Look for children that have layout_weight set and are themselves layouts (nested layouts)
        boolean hasWeightedChildLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight != null && !weight.isEmpty()) {
                // Check if the child is a layout (a ViewGroup / container)
                if (isLayoutElement(childElement)) {
                    hasWeightedChildLayout = true;
                    break;
                }
            }
        }

        if (hasWeightedChildLayout) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance");
        }
    }

    private static boolean isLayoutElement(@NonNull Element element) {
        String tag = element.getTagName();
        // Common layout containers
        return tag.endsWith("Layout")
                || tag.equals("ScrollView")
                || tag.equals("HorizontalScrollView")
                || tag.equals("ViewGroup")
                || tag.equals("FrameLayout")
                || tag.equals("GridView")
                || tag.equals("ListView")
                || tag.equals("RelativeLayout")
                || tag.equals("LinearLayout")
                || tag.equals("TableLayout")
                || tag.equals("TableRow")
                || tag.equals("merge")
                || tag.equals("include")
                || tag.contains(".");
    }
}