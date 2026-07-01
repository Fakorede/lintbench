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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks whether layout_* params are valid for the given parent layout.
 */
public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given `layout_param` is not defined for the given layout, meaning it has no " +
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

    // Layout param attributes defined by ViewGroup.LayoutParams
    private static final Set<String> LAYOUT_PARAMS_BASE = new HashSet<>(Arrays.asList(
            "layout_width",
            "layout_height"
    ));

    // Layout param attributes defined by ViewGroup.MarginLayoutParams
    private static final Set<String> LAYOUT_PARAMS_MARGIN = new HashSet<>(Arrays.asList(
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

    // LinearLayout-specific layout params
    private static final Set<String> LAYOUT_PARAMS_LINEAR = new HashSet<>(Arrays.asList(
            "layout_weight",
            "layout_gravity"
    ));

    // RelativeLayout-specific layout params
    private static final Set<String> LAYOUT_PARAMS_RELATIVE = new HashSet<>(Arrays.asList(
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
            "layout_centerInParent",
            "layout_centerHorizontal",
            "layout_centerVertical",
            "layout_alignWithParentIfMissing"
    ));

    // GridLayout-specific layout params
    private static final Set<String> LAYOUT_PARAMS_GRID = new HashSet<>(Arrays.asList(
            "layout_row",
            "layout_rowSpan",
            "layout_rowWeight",
            "layout_column",
            "layout_columnSpan",
            "layout_columnWeight",
            "layout_gravity"
    ));

    // TableLayout-specific layout params (TableLayout extends LinearLayout)
    private static final Set<String> LAYOUT_PARAMS_TABLE = new HashSet<>(Arrays.asList(
            "layout_column",
            "layout_span",
            "layout_gravity"
    ));

    // FrameLayout-specific layout params
    private static final Set<String> LAYOUT_PARAMS_FRAME = new HashSet<>(Arrays.asList(
            "layout_gravity"
    ));

    // AbsoluteLayout-specific layout params (deprecated)
    private static final Set<String> LAYOUT_PARAMS_ABSOLUTE = new HashSet<>(Arrays.asList(
            "layout_x",
            "layout_y"
    ));

    // ActionBar-specific layout params
    private static final Set<String> LAYOUT_PARAMS_ACTION_BAR = new HashSet<>(Arrays.asList(
            "layout_gravity"
    ));

    // CoordinatorLayout-specific layout params
    private static final Set<String> LAYOUT_PARAMS_COORDINATOR = new HashSet<>(Arrays.asList(
            "layout_behavior",
            "layout_anchor",
            "layout_anchorGravity",
            "layout_keyline",
            "layout_dodgeInsetEdges",
            "layout_insetEdge"
    ));

    // AppBarLayout-specific layout params
    private static final Set<String> LAYOUT_PARAMS_APP_BAR = new HashSet<>(Arrays.asList(
            "layout_scrollFlags",
            "layout_scrollInterpolator"
    ));

    // CollapsingToolbarLayout-specific layout params
    private static final Set<String> LAYOUT_PARAMS_COLLAPSING_TOOLBAR = new HashSet<>(Arrays.asList(
            "layout_collapseMode",
            "layout_collapseParallaxMultiplier"
    ));

    // DrawerLayout-specific layout params
    private static final Set<String> LAYOUT_PARAMS_DRAWER = new HashSet<>(Arrays.asList(
            "layout_gravity"
    ));

    // ConstraintLayout-specific layout params
    private static final Set<String> LAYOUT_PARAMS_CONSTRAINT = new HashSet<>(Arrays.asList(
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
            "layout_constraintLeft_creator",
            "layout_constraintTop_creator",
            "layout_constraintRight_creator",
            "layout_constraintBottom_creator",
            "layout_constraintBaseline_creator",
            "layout_constraintDimensionRatio",
            "layout_constraintHorizontal_chainStyle",
            "layout_constraintVertical_chainStyle",
            "layout_constraintHorizontal_weight",
            "layout_constraintVertical_weight",
            "layout_constraintCircle",
            "layout_constraintCircleRadius",
            "layout_constraintCircleAngle",
            "layout_editor_absoluteX",
            "layout_editor_absoluteY",
            "layout_constraintTag",
            "layout_goneMarginBottom",
            "layout_goneMarginEnd",
            "layout_goneMarginLeft",
            "layout_goneMarginRight",
            "layout_goneMarginStart",
            "layout_goneMarginTop"
    ));

    /**
     * Map from parent layout tag name (simple, lowercase) to the set of
     * layout_* attributes that are VALID for children of that layout.
     */
    private static final Map<String, Set<String>> VALID_PARAMS_FOR_PARENT;

    static {
        VALID_PARAMS_FOR_PARENT = new HashMap<>();

        // Every layout supports base params
        Set<String> base = new HashSet<>(LAYOUT_PARAMS_BASE);
        Set<String> baseAndMargin = new HashSet<>(LAYOUT_PARAMS_BASE);
        baseAndMargin.addAll(LAYOUT_PARAMS_MARGIN);

        // LinearLayout
        Set<String> linearParams = new HashSet<>(baseAndMargin);
        linearParams.addAll(LAYOUT_PARAMS_LINEAR);
        VALID_PARAMS_FOR_PARENT.put("LinearLayout", linearParams);
        VALID_PARAMS_FOR_PARENT.put("linearlayout", linearParams);

        // RelativeLayout
        Set<String> relativeParams = new HashSet<>(baseAndMargin);
        relativeParams.addAll(LAYOUT_PARAMS_RELATIVE);
        VALID_PARAMS_FOR_PARENT.put("RelativeLayout", relativeParams);
        VALID_PARAMS_FOR_PARENT.put("relativelayout", relativeParams);

        // FrameLayout
        Set<String> frameParams = new HashSet<>(baseAndMargin);
        frameParams.addAll(LAYOUT_PARAMS_FRAME);
        VALID_PARAMS_FOR_PARENT.put("FrameLayout", frameParams);
        VALID_PARAMS_FOR_PARENT.put("framelayout", frameParams);

        // TableLayout
        Set<String> tableParams = new HashSet<>(baseAndMargin);
        tableParams.addAll(LAYOUT_PARAMS_TABLE);
        VALID_PARAMS_FOR_PARENT.put("TableLayout", tableParams);
        VALID_PARAMS_FOR_PARENT.put("tablelayout", tableParams);

        // TableRow (extends LinearLayout)
        Set<String> tableRowParams = new HashSet<>(baseAndMargin);
        tableRowParams.addAll(LAYOUT_PARAMS_TABLE);
        VALID_PARAMS_FOR_PARENT.put("TableRow", tableRowParams);
        VALID_PARAMS_FOR_PARENT.put("tablerow", tableRowParams);

        // GridLayout
        Set<String> gridParams = new HashSet<>(baseAndMargin);
        gridParams.addAll(LAYOUT_PARAMS_GRID);
        VALID_PARAMS_FOR_PARENT.put("GridLayout", gridParams);
        VALID_PARAMS_FOR_PARENT.put("gridlayout", gridParams);

        // AbsoluteLayout (deprecated)
        Set<String> absoluteParams = new HashSet<>(baseAndMargin);
        absoluteParams.addAll(LAYOUT_PARAMS_ABSOLUTE);
        VALID_PARAMS_FOR_PARENT.put("AbsoluteLayout", absoluteParams);
        VALID_PARAMS_FOR_PARENT.put("absolutelayout", absoluteParams);

        // ConstraintLayout
        Set<String> constraintParams = new HashSet<>(baseAndMargin);
        constraintParams.addAll(LAYOUT_PARAMS_CONSTRAINT);
        VALID_PARAMS_FOR_PARENT.put("ConstraintLayout", constraintParams);
        VALID_PARAMS_FOR_PARENT.put("android.support.constraint.ConstraintLayout", constraintParams);
        VALID_PARAMS_FOR_PARENT.put("androidx.constraintlayout.widget.ConstraintLayout", constraintParams);

        // CoordinatorLayout
        Set<String> coordinatorParams = new HashSet<>(baseAndMargin);
        coordinatorParams.addAll(LAYOUT_PARAMS_COORDINATOR);
        VALID_PARAMS_FOR_PARENT.put("CoordinatorLayout", coordinatorParams);
        VALID_PARAMS_FOR_PARENT.put("android.support.design.widget.CoordinatorLayout", coordinatorParams);
        VALID_PARAMS_FOR_PARENT.put("androidx.coordinatorlayout.widget.CoordinatorLayout", coordinatorParams);

        // AppBarLayout
        Set<String> appBarParams = new HashSet<>(baseAndMargin);
        appBarParams.addAll(LAYOUT_PARAMS_APP_BAR);
        VALID_PARAMS_FOR_PARENT.put("AppBarLayout", appBarParams);
        VALID_PARAMS_FOR_PARENT.put("android.support.design.widget.AppBarLayout", appBarParams);
        VALID_PARAMS_FOR_PARENT.put("com.google.android.material.appbar.AppBarLayout", appBarParams);

        // CollapsingToolbarLayout
        Set<String> collapsingParams = new HashSet<>(baseAndMargin);
        collapsingParams.addAll(LAYOUT_PARAMS_COLLAPSING_TOOLBAR);
        VALID_PARAMS_FOR_PARENT.put("CollapsingToolbarLayout", collapsingParams);
        VALID_PARAMS_FOR_PARENT.put("android.support.design.widget.CollapsingToolbarLayout", collapsingParams);
        VALID_PARAMS_FOR_PARENT.put("com.google.android.material.appbar.CollapsingToolbarLayout", collapsingParams);

        // DrawerLayout
        Set<String> drawerParams = new HashSet<>(baseAndMargin);
        drawerParams.addAll(LAYOUT_PARAMS_DRAWER);
        VALID_PARAMS_FOR_PARENT.put("DrawerLayout", drawerParams);
        VALID_PARAMS_FOR_PARENT.put("android.support.v4.widget.DrawerLayout", drawerParams);
        VALID_PARAMS_FOR_PARENT.put("androidx.drawerlayout.widget.DrawerLayout", drawerParams);

        // ScrollView (extends FrameLayout)
        VALID_PARAMS_FOR_PARENT.put("ScrollView", frameParams);
        VALID_PARAMS_FOR_PARENT.put("HorizontalScrollView", frameParams);

        // ViewPager
        Set<String> viewPagerParams = new HashSet<>(base);
        VALID_PARAMS_FOR_PARENT.put("ViewPager", viewPagerParams);
        VALID_PARAMS_FOR_PARENT.put("android.support.v4.view.ViewPager", viewPagerParams);
        VALID_PARAMS_FOR_PARENT.put("androidx.viewpager.widget.ViewPager", viewPagerParams);
    }

    /**
     * All known layout_* attribute local names that we track across all layouts.
     * Any attribute starting with "layout_" that is not in the valid set for the
     * parent is flagged.
     */
    private static final Set<String> ALL_KNOWN_LAYOUT_PARAMS;

    static {
        ALL_KNOWN_LAYOUT_PARAMS = new HashSet<>();
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_BASE);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_MARGIN);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_LINEAR);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_RELATIVE);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_GRID);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_TABLE);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_FRAME);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_ABSOLUTE);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_ACTION_BAR);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_COORDINATOR);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_APP_BAR);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_COLLAPSING_TOOLBAR);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_DRAWER);
        ALL_KNOWN_LAYOUT_PARAMS.addAll(LAYOUT_PARAMS_CONSTRAINT);
    }

    /** Constructs a new {@link ObsoleteLayoutParamsDetector} */
    public ObsoleteLayoutParamsDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        // We want to check all attributes; return null to check all,
        // but we'll filter to layout_* ones manually.
        // Actually, returning a specific list is more efficient.
        List<String> attrs = new ArrayList<>(ALL_KNOWN_LAYOUT_PARAMS);
        return attrs;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        // layout_width and layout_height are always valid (except on root, but
        // that's a different check)
        if (name.equals("layout_width") || name.equals("layout_height")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();

        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Root element or no parent — layout params on root are generally
            // irrelevant but that's a separate concern; skip here.
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        // Resolve <merge> — if the parent is merge, we can't determine the real
        // parent layout, so skip.
        if ("merge".equals(parentTag)) {
            return;
        }

        // Look up valid params for this parent
        Set<String> validParams = getValidParamsForParent(parentTag);
        if (validParams == null) {
            // Unknown parent — we can't validate, so skip to avoid false positives.
            return;
        }

        if (!validParams.contains(name)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "Invalid layout param in a `%1$s`: `%2$s`",
                            getSimpleName(parentTag),
                            name));
        }
    }

    /**
     * Returns the set of valid layout_* attribute names for children of the
     * given parent tag, or {@code null} if the parent is unknown/custom.
     */
    @Nullable
    private static Set<String> getValidParamsForParent(@NonNull String parentTag) {
        // Direct lookup
        Set<String> params = VALID_PARAMS_FOR_PARENT.get(parentTag);
        if (params != null) {
            return params;
        }

        // Try simple name (strip package prefix)
        String simpleName = getSimpleName(parentTag);
        params = VALID_PARAMS_FOR_PARENT.get(simpleName);
        if (params != null) {
            return params;
        }

        // Unknown layout — return null to avoid false positives on custom views
        return null;
    }

    /**
     * Returns the simple class name from a fully-qualified name.
     * E.g. "android.widget.LinearLayout" -> "LinearLayout"
     */
    @NonNull
    private static String getSimpleName(@NonNull String tag) {
        int lastDot = tag.lastIndexOf('.');
        if (lastDot >= 0) {
            return tag.substring(lastDot + 1);
        }
        return tag;
    }
}