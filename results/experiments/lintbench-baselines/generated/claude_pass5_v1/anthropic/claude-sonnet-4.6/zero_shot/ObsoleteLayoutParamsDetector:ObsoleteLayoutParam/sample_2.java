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
 * Checks for obsolete layout params (layout params that are defined on a view
 * but don't apply to the parent layout).
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

    // Layout param attributes that are defined by specific parent layouts

    /** Params defined by AbsoluteLayout */
    private static final Set<String> ABSOLUTE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_x",
            "layout_y"
    ));

    /** Params defined by LinearLayout */
    private static final Set<String> LINEAR_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_gravity",
            "layout_weight"
    ));

    /** Params defined by RelativeLayout */
    private static final Set<String> RELATIVE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_above",
            "layout_alignBaseline",
            "layout_alignBottom",
            "layout_alignEnd",
            "layout_alignLeft",
            "layout_alignParentBottom",
            "layout_alignParentEnd",
            "layout_alignParentLeft",
            "layout_alignParentRight",
            "layout_alignParentStart",
            "layout_alignParentTop",
            "layout_alignRight",
            "layout_alignStart",
            "layout_alignTop",
            "layout_alignWithParentIfMissing",
            "layout_below",
            "layout_centerHorizontal",
            "layout_centerInParent",
            "layout_centerVertical",
            "layout_toEndOf",
            "layout_toLeftOf",
            "layout_toRightOf",
            "layout_toStartOf"
    ));

    /** Params defined by GridLayout */
    private static final Set<String> GRID_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_column",
            "layout_columnSpan",
            "layout_columnWeight",
            "layout_gravity",
            "layout_row",
            "layout_rowSpan",
            "layout_rowWeight"
    ));

    /** Params defined by TableLayout (child must be TableRow) */
    private static final Set<String> TABLE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_gravity",
            "layout_weight"
    ));

    /** Params defined by TableRow */
    private static final Set<String> TABLE_ROW_PARAMS = new HashSet<>(Arrays.asList(
            "layout_column",
            "layout_span"
    ));

    /**
     * Params that are defined by FrameLayout (and FrameLayout subclasses like
     * ScrollView, etc.)
     */
    private static final Set<String> FRAME_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_gravity"
    ));

    /**
     * Map from layout tag names to their specific layout params (in addition to
     * the base ViewGroup.LayoutParams which includes layout_width and
     * layout_height).
     */
    private static final Map<String, Set<String>> LAYOUT_PARAMS;

    static {
        LAYOUT_PARAMS = new HashMap<>();
        LAYOUT_PARAMS.put("AbsoluteLayout", ABSOLUTE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("LinearLayout", LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("RelativeLayout", RELATIVE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("GridLayout", GRID_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("TableLayout", TABLE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("TableRow", TABLE_ROW_PARAMS);
        LAYOUT_PARAMS.put("FrameLayout", FRAME_LAYOUT_PARAMS);
        // FrameLayout subclasses
        LAYOUT_PARAMS.put("ScrollView", FRAME_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("HorizontalScrollView", FRAME_LAYOUT_PARAMS);
        // LinearLayout subclasses
        LAYOUT_PARAMS.put("RadioGroup", LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("SearchView", LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("SlidingDrawer", LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("TabWidget", LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("Toolbar", LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("ZoomControls", LINEAR_LAYOUT_PARAMS);
        // GridLayout subclasses
        LAYOUT_PARAMS.put("android.support.v7.widget.GridLayout", GRID_LAYOUT_PARAMS);
        // RelativeLayout subclasses
        LAYOUT_PARAMS.put("DialerFilter", RELATIVE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("TwoLineListItem", RELATIVE_LAYOUT_PARAMS);
    }

    /**
     * Layout params that are valid for ALL parent layouts (defined by
     * ViewGroup.LayoutParams and ViewGroup.MarginLayoutParams).
     */
    private static final Set<String> COMMON_PARAMS = new HashSet<>(Arrays.asList(
            "layout_width",
            "layout_height",
            "layout_margin",
            "layout_marginBottom",
            "layout_marginEnd",
            "layout_marginLeft",
            "layout_marginRight",
            "layout_marginStart",
            "layout_marginTop",
            "layout_marginHorizontal",
            "layout_marginVertical"
    ));

    /** Constructs a new {@link ObsoleteLayoutParamsDetector} */
    public ObsoleteLayoutParamsDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        // Collect all layout params that are layout-specific
        Set<String> all = new HashSet<>();
        for (Set<String> params : LAYOUT_PARAMS.values()) {
            all.addAll(params);
        }
        return all;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        // Skip common params that are valid everywhere
        if (COMMON_PARAMS.contains(name)) {
            return;
        }

        // Find the parent element of this attribute's owner element
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // No parent element (e.g., root element), layout params don't apply
            // but we only warn for known bad combos
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();

        // Get the simple name of the parent tag (strip package prefix if present)
        String parentSimpleName = parentTag;
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex >= 0) {
            parentSimpleName = parentTag.substring(dotIndex + 1);
        }

        // Check if the parent tag is one we know about
        Set<String> validParams = LAYOUT_PARAMS.get(parentTag);
        if (validParams == null) {
            validParams = LAYOUT_PARAMS.get(parentSimpleName);
        }

        if (validParams == null) {
            // We don't know about this layout, so we can't say whether the param is valid
            // But if it's a known layout-specific param, check all known layouts
            // to see if this param belongs to a DIFFERENT known layout
            boolean paramBelongsToKnownLayout = false;
            for (Map.Entry<String, Set<String>> entry : LAYOUT_PARAMS.entrySet()) {
                if (entry.getValue().contains(name)) {
                    paramBelongsToKnownLayout = true;
                    break;
                }
            }
            // We can't determine if it's obsolete for unknown parent layouts
            return;
        }

        // Check if the attribute is valid for the parent layout
        if (!validParams.contains(name)) {
            // The param is not valid for this parent layout
            // Make sure it's not a common param either
            if (!COMMON_PARAMS.contains(name)) {
                String message = String.format(
                        "Invalid layout param '%1$s' (not defined by parent layout '%2$s')",
                        name, parentTag);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        }
    }
}