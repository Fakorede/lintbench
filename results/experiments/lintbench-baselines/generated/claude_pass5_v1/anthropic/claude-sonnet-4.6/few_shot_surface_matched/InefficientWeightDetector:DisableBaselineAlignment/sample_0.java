package com.android.tools.lint.checks;

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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_FALSE;

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
                    new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if baselineAligned is already set to false
        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (VALUE_FALSE.equals(baselineAligned)) {
            return;
        }

        // Check if any child views have a layout_weight and are themselves layouts (ViewGroups)
        NodeList children = element.getChildNodes();
        boolean hasWeightedChildLayout = false;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight != null && !weight.isEmpty()) {
                // Check if this child is a layout (a ViewGroup-like element)
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
        // Common layout/ViewGroup names
        return tag.equals(LINEAR_LAYOUT)
                || tag.equals("RelativeLayout")
                || tag.equals("FrameLayout")
                || tag.equals("TableLayout")
                || tag.equals("TableRow")
                || tag.equals("GridLayout")
                || tag.equals("ScrollView")
                || tag.equals("HorizontalScrollView")
                || tag.equals("ListView")
                || tag.equals("GridView")
                || tag.equals("ConstraintLayout")
                || tag.equals("androidx.constraintlayout.widget.ConstraintLayout")
                || tag.equals("android.widget.LinearLayout")
                || tag.equals("android.widget.RelativeLayout")
                || tag.equals("android.widget.FrameLayout")
                || tag.equals("android.widget.TableLayout")
                || tag.equals("android.widget.TableRow")
                || tag.equals("android.widget.GridLayout")
                || tag.equals("android.widget.ScrollView")
                || tag.equals("android.widget.HorizontalScrollView")
                || tag.equals("android.widget.ListView")
                || tag.equals("android.widget.GridView")
                // Heuristic: any tag ending in "Layout" is likely a ViewGroup
                || tag.endsWith("Layout");
    }
}