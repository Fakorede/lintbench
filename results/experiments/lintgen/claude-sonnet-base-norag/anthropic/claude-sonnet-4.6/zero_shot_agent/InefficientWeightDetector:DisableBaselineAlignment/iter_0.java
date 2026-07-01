/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

/**
 * Checks whether a LinearLayout with weights has baseline alignment enabled,
 * which can be inefficient.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Issue for missing baselineAligned attribute */
    public static final Issue BASELINE_WEIGHTS = Issue.create(
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
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link InefficientWeightDetector} */
    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only care about horizontal LinearLayouts (or those without orientation, which
        // defaults to horizontal)
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            // Vertical LinearLayouts don't do baseline alignment
            return;
        }

        // Check if baselineAligned is already set to false
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED)) {
            String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
            if ("false".equals(baselineAligned)) {
                // Already disabled, no issue
                return;
            }
            // If it's explicitly set to true, we still want to warn if there are
            // nested layouts with weights, but only if it's not explicitly set.
            // Actually, if it's explicitly set to true, the developer made a conscious
            // choice, so we skip the warning.
            return;
        }

        // Check if any child has a layout_weight attribute AND is a layout (ViewGroup)
        boolean hasWeightedLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            // Check if this child has a layout_weight
            if (!childElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                continue;
            }

            // Check if this child is a layout (i.e., a ViewGroup / nested layout)
            // We consider it a layout if its tag name contains "Layout" or is a known
            // container widget
            String tagName = childElement.getTagName();
            if (isLayout(tagName)) {
                hasWeightedLayout = true;
                break;
            }
        }

        if (hasWeightedLayout) {
            context.report(
                    BASELINE_WEIGHTS,
                    element,
                    context.getLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance");
        }
    }

    /**
     * Returns true if the given tag name represents a layout (ViewGroup).
     */
    private static boolean isLayout(String tagName) {
        // Check simple name (after last dot)
        String simpleName = tagName;
        int dotIndex = tagName.lastIndexOf('.');
        if (dotIndex >= 0) {
            simpleName = tagName.substring(dotIndex + 1);
        }

        // Common layout names end with "Layout" or are known containers
        if (simpleName.endsWith("Layout")) {
            return true;
        }

        // Other known ViewGroup containers
        switch (simpleName) {
            case "ScrollView":
            case "HorizontalScrollView":
            case "FrameLayout":
            case "RelativeLayout":
            case "LinearLayout":
            case "GridLayout":
            case "TableLayout":
            case "TableRow":
            case "ViewGroup":
            case "RadioGroup":
            case "SlidingDrawer":
            case "TabHost":
            case "TabWidget":
            case "ViewAnimator":
            case "ViewFlipper":
            case "ViewSwitcher":
            case "TextSwitcher":
            case "ImageSwitcher":
            case "AdapterView":
            case "ListView":
            case "GridView":
            case "ExpandableListView":
            case "Spinner":
            case "Gallery":
            case "StackView":
            case "AdapterViewFlipper":
            case "RecyclerView":
            case "CardView":
            case "DrawerLayout":
            case "CoordinatorLayout":
            case "ConstraintLayout":
            case "MotionLayout":
            case "AppBarLayout":
            case "CollapsingToolbarLayout":
            case "ToolbarLayout":
            case "NavigationView":
            case "BottomNavigationView":
            case "ViewPager":
            case "ViewPager2":
            case "SurfaceView":
            case "TextureView":
                return true;
            default:
                return false;
        }
    }
}