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
        // Check if baselineAligned is already set to false
        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (baselineAligned != null && baselineAligned.equals("false")) {
            // Already disabled, no issue
            return;
        }

        // Check if this is a vertical LinearLayout (baseline alignment only matters for horizontal)
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        // Check if any child has a layout_weight and is a layout (ViewGroup)
        boolean hasWeightedChildLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight != null && !weight.isEmpty()) {
                // Check if the child is a layout (ViewGroup subclass)
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
                    "Set `android:baselineAligned=\"false\"` on this element for better performance");
        }
    }

    /**
     * Returns true if the given tag name represents a layout (ViewGroup) rather than a simple View.
     */
    private static boolean isLayout(String tagName) {
        // Common layout ViewGroup subclasses
        switch (tagName) {
            case "LinearLayout":
            case "RelativeLayout":
            case "FrameLayout":
            case "TableLayout":
            case "TableRow":
            case "GridLayout":
            case "ConstraintLayout":
            case "androidx.constraintlayout.widget.ConstraintLayout":
            case "CoordinatorLayout":
            case "androidx.coordinatorlayout.widget.CoordinatorLayout":
            case "ScrollView":
            case "HorizontalScrollView":
            case "NestedScrollView":
            case "androidx.core.widget.NestedScrollView":
            case "ViewGroup":
            case "RadioGroup":
            case "GridView":
            case "ListView":
            case "RecyclerView":
            case "androidx.recyclerview.widget.RecyclerView":
            case "ViewPager":
            case "ViewPager2":
            case "androidx.viewpager.widget.ViewPager":
            case "androidx.viewpager2.widget.ViewPager2":
            case "DrawerLayout":
            case "androidx.drawerlayout.widget.DrawerLayout":
            case "merge":
            case "include":
                return true;
            default:
                // Heuristic: if it ends with "Layout" it's likely a ViewGroup
                return tagName.endsWith("Layout") || tagName.endsWith("View") && !tagName.equals("View") && !tagName.equals("TextView") && !tagName.equals("ImageView") && !tagName.equals("EditText") && !tagName.equals("Button") && !tagName.equals("CheckBox") && !tagName.equals("RadioButton") && !tagName.equals("Switch") && !tagName.equals("ToggleButton") && !tagName.equals("ProgressBar") && !tagName.equals("SeekBar") && !tagName.equals("RatingBar") && !tagName.equals("Spinner") && !tagName.equals("WebView") && !tagName.equals("VideoView") && !tagName.equals("SurfaceView") && !tagName.equals("TextureView");
        }
    }
}