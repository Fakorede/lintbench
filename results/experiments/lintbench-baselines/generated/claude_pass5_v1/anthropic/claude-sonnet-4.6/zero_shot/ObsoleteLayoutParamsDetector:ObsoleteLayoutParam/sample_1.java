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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks for obsolete layout params (layout params that are not valid for the
 * given parent layout).
 */
public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_LAYOUT_PREFIX = "layout_";

    // Layout params defined by ViewGroup (valid in all layouts)
    private static final Set<String> VIEW_GROUP_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_width",
            "layout_height"
    ));

    // Layout params defined by ViewGroup.MarginLayoutParams (valid in layouts that use margin)
    private static final Set<String> MARGIN_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_margin",
            "layout_marginLeft",
            "layout_marginTop",
            "layout_marginRight",
            "layout_marginBottom",
            "layout_marginStart",
            "layout_marginEnd",
            "layout_marginHorizontal",
            "layout_marginVertical"
    ));

    // Layout params specific to LinearLayout
    private static final Set<String> LINEAR_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_weight",
            "layout_gravity"
    ));

    // Layout params specific to RelativeLayout
    private static final Set<String> RELATIVE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_above",
            "layout_below",
            "layout_toLeftOf",
            "layout_toRightOf",
            "layout_toStartOf",
            "layout_toEndOf",
            "layout_alignTop",
            "layout_alignBottom",
            "layout_alignLeft",
            "layout_alignRight",
            "layout_alignStart",
            "layout_alignEnd",
            "layout_alignBaseline",
            "layout_alignParentTop",
            "layout_alignParentBottom",
            "layout_alignParentLeft",
            "layout_alignParentRight",
            "layout_alignParentStart",
            "layout_alignParentEnd",
            "layout_centerHorizontal",
            "layout_centerVertical",
            "layout_centerInParent",
            "layout_gravity"
    ));

    // Layout params specific to FrameLayout
    private static final Set<String> FRAME_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_gravity"
    ));

    // Layout params specific to GridLayout
    private static final Set<String> GRID_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_row",
            "layout_rowSpan",
            "layout_rowWeight",
            "layout_column",
            "layout_columnSpan",
            "layout_columnWeight",
            "layout_gravity"
    ));

    // Layout params specific to TableLayout / TableRow
    private static final Set<String> TABLE_ROW_PARAMS = new HashSet<>(Arrays.asList(
            "layout_column",
            "layout_span",
            "layout_gravity"
    ));

    // Layout params specific to GridView
    private static final Set<String> GRID_VIEW_PARAMS = new HashSet<>(Arrays.asList(
            "layout_column"
    ));

    // Layout params specific to AbsoluteLayout (deprecated)
    private static final Set<String> ABSOLUTE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_x",
            "layout_y"
    ));

    // Layout params specific to ConstraintLayout
    private static final Set<String> CONSTRAINT_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintHorizontal_bias",
            "layout_constraintVertical_bias",
            "layout_constraintWidth_default",
            "layout_constraintHeight_default",
            "layout_constraintWidth_min",
            "layout_constraintWidth_max",
            "layout_constraintWidth_percent",
            "layout_constraintHeight_min",
            "layout_constraintHeight_max",
            "layout_constraintHeight_percent",
            "layout_constraintCircle",
            "layout_constraintCircleAngle",
            "layout_constraintCircleRadius",
            "layout_constraintDimensionRatio",
            "layout_constraintGuide_begin",
            "layout_constraintGuide_end",
            "layout_constraintGuide_percent",
            "layout_constraintHorizontal_chainStyle",
            "layout_constraintVertical_chainStyle",
            "layout_constraintHorizontal_weight",
            "layout_constraintVertical_weight",
            "layout_constraintTag",
            "layout_editor_absoluteX",
            "layout_editor_absoluteY",
            "layout_goneMarginLeft",
            "layout_goneMarginTop",
            "layout_goneMarginRight",
            "layout_goneMarginBottom",
            "layout_goneMarginStart",
            "layout_goneMarginEnd",
            "layout_optimizationLevel",
            "layout_constraintWidth",
            "layout_constraintHeight",
            "layout_wrapBehaviorInParent"
    ));

    // Layout params specific to CoordinatorLayout
    private static final Set<String> COORDINATOR_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_anchor",
            "layout_anchorGravity",
            "layout_behavior",
            "layout_dodgeInsetEdges",
            "layout_gravity",
            "layout_insetEdge",
            "layout_keyline"
    ));

    // Layout params specific to DrawerLayout
    private static final Set<String> DRAWER_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_gravity"
    ));

    // Mapping from layout tag names to their valid layout params
    // (in addition to the base ViewGroup params)
    private static final Map<String, Set<String>> LAYOUT_TO_PARAMS = new HashMap<>();

    static {
        LAYOUT_TO_PARAMS.put("LinearLayout", union(MARGIN_LAYOUT_PARAMS, LINEAR_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("RadioGroup", union(MARGIN_LAYOUT_PARAMS, LINEAR_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("RelativeLayout", union(MARGIN_LAYOUT_PARAMS, RELATIVE_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("FrameLayout", union(MARGIN_LAYOUT_PARAMS, FRAME_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("GridLayout", union(MARGIN_LAYOUT_PARAMS, GRID_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("TableLayout", union(MARGIN_LAYOUT_PARAMS, LINEAR_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("TableRow", union(MARGIN_LAYOUT_PARAMS, TABLE_ROW_PARAMS));
        LAYOUT_TO_PARAMS.put("GridView", union(MARGIN_LAYOUT_PARAMS, GRID_VIEW_PARAMS));
        LAYOUT_TO_PARAMS.put("AbsoluteLayout", union(MARGIN_LAYOUT_PARAMS, ABSOLUTE_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("ScrollView", union(MARGIN_LAYOUT_PARAMS, FRAME_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("HorizontalScrollView", union(MARGIN_LAYOUT_PARAMS, FRAME_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("ViewPager", MARGIN_LAYOUT_PARAMS);
        LAYOUT_TO_PARAMS.put("ViewPager2", MARGIN_LAYOUT_PARAMS);
        LAYOUT_TO_PARAMS.put("RecyclerView", MARGIN_LAYOUT_PARAMS);
        LAYOUT_TO_PARAMS.put("ConstraintLayout", union(MARGIN_LAYOUT_PARAMS, CONSTRAINT_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("CoordinatorLayout", union(MARGIN_LAYOUT_PARAMS, COORDINATOR_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("DrawerLayout", union(MARGIN_LAYOUT_PARAMS, DRAWER_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("AppBarLayout", union(MARGIN_LAYOUT_PARAMS, LINEAR_LAYOUT_PARAMS));
        LAYOUT_TO_PARAMS.put("CollapsingToolbarLayout", union(MARGIN_LAYOUT_PARAMS, new HashSet<>(Arrays.asList(
                "layout_collapseMode",
                "layout_collapseParallaxMultiplier",
                "layout_gravity"
        ))));
        LAYOUT_TO_PARAMS.put("MotionLayout", union(MARGIN_LAYOUT_PARAMS, CONSTRAINT_LAYOUT_PARAMS));
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> result = new HashSet<>();
        for (Set<String> set : sets) {
            result.addAll(set);
        }
        return result;
    }

    /** Constructs a new {@link ObsoleteLayoutParamsDetector} */
    public ObsoleteLayoutParamsDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We only care about elements that have a parent which is a layout
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = getBaseName(parent.getTagName());

        // If we don't know the parent layout, we can't check params
        if (parentTag == null) {
            return;
        }

        Set<String> validParams = LAYOUT_TO_PARAMS.get(parentTag);
        if (validParams == null) {
            // Unknown layout - we can't validate
            return;
        }

        // Check each attribute on this element
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
            }

            if (name != null && name.startsWith(ATTR_LAYOUT_PREFIX)) {
                // This is a layout param
                if (!VIEW_GROUP_LAYOUT_PARAMS.contains(name) && !validParams.contains(name)) {
                    // Check if it's a known param for ANY layout - if not, it might be custom
                    if (isKnownLayoutParam(name)) {
                        context.report(
                                ISSUE,
                                attr,
                                context.getValueLocation(attr),
                                String.format(
                                        "Invalid layout param in a `%1$s`: `%2$s`",
                                        parentTag, name));
                    }
                }
            }
        }
    }

    /**
     * Returns the simple (unqualified) tag name for a layout element.
     * For example, "android.widget.LinearLayout" -> "LinearLayout",
     * "LinearLayout" -> "LinearLayout".
     */
    @Nullable
    private static String getBaseName(@Nullable String tag) {
        if (tag == null) {
            return null;
        }
        int index = tag.lastIndexOf('.');
        if (index != -1) {
            return tag.substring(index + 1);
        }
        return tag;
    }

    /**
     * Returns true if the given attribute name is a known layout param
     * (defined in any of the known layout param sets). This helps avoid
     * false positives for custom layout params.
     */
    private static boolean isKnownLayoutParam(@NonNull String name) {
        for (Set<String> params : LAYOUT_TO_PARAMS.values()) {
            if (params.contains(name)) {
                return true;
            }
        }
        if (MARGIN_LAYOUT_PARAMS.contains(name)) {
            return true;
        }
        if (LINEAR_LAYOUT_PARAMS.contains(name)) {
            return true;
        }
        if (RELATIVE_LAYOUT_PARAMS.contains(name)) {
            return true;
        }
        if (FRAME_LAYOUT_PARAMS.contains(name)) {
            return true;
        }
        if (GRID_LAYOUT_PARAMS.contains(name)) {
            return true;
        }
        if (TABLE_ROW_PARAMS.contains(name)) {
            return true;
        }
        if (ABSOLUTE_LAYOUT_PARAMS.contains(name)) {
            return true;
        }
        if (CONSTRAINT_LAYOUT_PARAMS.contains(name)) {
            return true;
        }
        return false;
    }
}