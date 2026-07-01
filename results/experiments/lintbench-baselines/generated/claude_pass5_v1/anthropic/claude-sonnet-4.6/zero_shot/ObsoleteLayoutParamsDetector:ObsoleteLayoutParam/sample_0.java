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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks for obsolete layout params (layout params that are defined on a view
 * but do not apply to the parent layout).
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

    // Layout param attributes defined by specific layouts

    /** Params defined by AbsoluteLayout */
    private static final Set<String> ABS_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
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

    /** Params defined by TableLayout / TableRow */
    private static final Set<String> TABLE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_column",
            "layout_span"
    ));

    /** Params defined by FrameLayout */
    private static final Set<String> FRAME_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_gravity"
    ));

    /**
     * Params that are defined by ViewGroup.LayoutParams and are valid in ALL
     * layouts.
     */
    private static final Set<String> GENERAL_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_width",
            "layout_height"
    ));

    /**
     * Params that are defined by ViewGroup.MarginLayoutParams and are valid in
     * most layouts (those that extend MarginLayoutParams).
     */
    private static final Set<String> MARGIN_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
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

    /**
     * Map from layout tag names to the set of layout params they support
     * (beyond the general ones).
     */
    private static final Map<String, Set<String>> LAYOUT_PARAMS;

    static {
        LAYOUT_PARAMS = new HashMap<>();
        LAYOUT_PARAMS.put("AbsoluteLayout", ABS_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("LinearLayout", LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("RelativeLayout", RELATIVE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("GridLayout", GRID_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("android.support.v7.widget.GridLayout", GRID_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("androidx.gridlayout.widget.GridLayout", GRID_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("TableLayout", TABLE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("TableRow", TABLE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("FrameLayout", FRAME_LAYOUT_PARAMS);
        // Layouts that extend FrameLayout
        LAYOUT_PARAMS.put("ScrollView", FRAME_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("HorizontalScrollView", FRAME_LAYOUT_PARAMS);
        // Layouts that extend LinearLayout
        LAYOUT_PARAMS.put("RadioGroup", LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("SearchView", LINEAR_LAYOUT_PARAMS);
        // Layouts that extend RelativeLayout
        LAYOUT_PARAMS.put("DialerFilter", RELATIVE_LAYOUT_PARAMS);
    }

    /**
     * All known layout-specific param attribute names (those that are only
     * valid under specific parent layouts).
     */
    private static final Set<String> ALL_SPECIFIC_LAYOUT_PARAMS;

    static {
        ALL_SPECIFIC_LAYOUT_PARAMS = new HashSet<>();
        ALL_SPECIFIC_LAYOUT_PARAMS.addAll(ABS_LAYOUT_PARAMS);
        ALL_SPECIFIC_LAYOUT_PARAMS.addAll(LINEAR_LAYOUT_PARAMS);
        ALL_SPECIFIC_LAYOUT_PARAMS.addAll(RELATIVE_LAYOUT_PARAMS);
        ALL_SPECIFIC_LAYOUT_PARAMS.addAll(GRID_LAYOUT_PARAMS);
        ALL_SPECIFIC_LAYOUT_PARAMS.addAll(TABLE_LAYOUT_PARAMS);
        ALL_SPECIFIC_LAYOUT_PARAMS.addAll(FRAME_LAYOUT_PARAMS);
    }

    /** Constructs a new {@link ObsoleteLayoutParamsDetector} */
    public ObsoleteLayoutParamsDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.unmodifiableSet(ALL_SPECIFIC_LAYOUT_PARAMS);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        // Find the parent element (the layout container)
        Node parentNode = attribute.getOwnerElement().getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // This view is the root element; layout params don't apply
            // (though layout_width/height are still needed for the inflater).
            // For root elements, there's no parent layout to validate against.
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();

        // Strip package prefix to get simple name for lookup
        String simpleParentTag = parentTag;
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex >= 0) {
            simpleParentTag = parentTag.substring(dotIndex + 1);
        }

        // Check if the parent layout is known
        Set<String> validParams = LAYOUT_PARAMS.get(parentTag);
        if (validParams == null) {
            validParams = LAYOUT_PARAMS.get(simpleParentTag);
        }

        if (validParams == null) {
            // Unknown parent layout - we can't determine validity, so skip
            return;
        }

        // Check if the attribute is valid for this parent layout
        if (!validParams.contains(name) &&
                !GENERAL_LAYOUT_PARAMS.contains(name) &&
                !MARGIN_LAYOUT_PARAMS.contains(name)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "Invalid layout param in a `%1$s`: `%2$s`",
                            parentTag,
                            name));
        }
    }
}