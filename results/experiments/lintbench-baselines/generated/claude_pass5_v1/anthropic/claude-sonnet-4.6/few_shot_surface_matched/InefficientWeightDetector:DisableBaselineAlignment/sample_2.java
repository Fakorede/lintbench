package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a `LinearLayout` is used to distribute the space proportionally between "
                            + "nested layouts, the baseline alignment property should be turned off "
                            + "to make the layout computation faster.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if baselineAligned is already explicitly set to false
        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (VALUE_FALSE.equals(baselineAligned)) {
            // Already disabled, no issue
            return;
        }

        // Check if any direct children have a layout_weight attribute
        // and are themselves layouts (ViewGroup subclasses)
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
                // Check if the child is a layout (i.e., a ViewGroup)
                String tag = childElement.getTagName();
                if (isLayout(tag)) {
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
                    "Set `android:baselineAligned=\"false\"` on this element for better performance");
        }
    }

    private static boolean isLayout(String tag) {
        // Common layout/ViewGroup class names
        if (tag.endsWith("Layout")) {
            return true;
        }
        switch (tag) {
            case "LinearLayout":
            case "RelativeLayout":
            case "FrameLayout":
            case "TableLayout":
            case "TableRow":
            case "GridLayout":
            case "ScrollView":
            case "HorizontalScrollView":
            case "ViewGroup":
            case "RadioGroup":
            case "SlidingDrawer":
            case "TabHost":
            case "TabWidget":
            case "ViewFlipper":
            case "ViewSwitcher":
            case "ViewAnimator":
            case "TextSwitcher":
            case "ImageSwitcher":
            case "GridView":
            case "ListView":
            case "ExpandableListView":
            case "Spinner":
            case "Gallery":
            case "AdapterView":
                return true;
            default:
                // If the tag contains a dot, it's a fully qualified class name;
                // we conservatively treat it as a potential layout.
                return tag.contains(".");
        }
    }
}