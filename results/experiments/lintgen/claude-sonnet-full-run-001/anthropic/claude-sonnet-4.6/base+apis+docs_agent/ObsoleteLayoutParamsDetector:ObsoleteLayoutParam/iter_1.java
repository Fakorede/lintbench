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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_ABOVE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING;
import static com.android.SdkConstants.ATTR_LAYOUT_BELOW;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_LAYOUT_X;
import static com.android.SdkConstants.ATTR_LAYOUT_Y;
import static com.android.SdkConstants.FRAME_LAYOUT;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.GRID_VIEW;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TABLE_LAYOUT;
import static com.android.SdkConstants.TABLE_ROW;

/**
 * Checks whether layout params are valid for the given parent layout.
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

    // Layout params that are valid for ALL layouts (defined in ViewGroup.LayoutParams)
    private static final Set<String> COMMON_PARAMS = new HashSet<>(Arrays.asList(
            ATTR_LAYOUT_WIDTH,
            ATTR_LAYOUT_HEIGHT
    ));

    // Layout params that are valid for layouts that support margins (ViewGroup.MarginLayoutParams)
    private static final Set<String> MARGIN_PARAMS = new HashSet<>(Arrays.asList(
            ATTR_LAYOUT_MARGIN,
            ATTR_LAYOUT_MARGIN_LEFT,
            ATTR_LAYOUT_MARGIN_RIGHT,
            ATTR_LAYOUT_MARGIN_TOP,
            ATTR_LAYOUT_MARGIN_BOTTOM,
            ATTR_LAYOUT_MARGIN_START,
            ATTR_LAYOUT_MARGIN_END
    ));

    // Layout params specific to LinearLayout
    private static final Set<String> LINEAR_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            ATTR_LAYOUT_GRAVITY,
            "layout_weight"
    ));

    // Layout params specific to RelativeLayout
    private static final Set<String> RELATIVE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            ATTR_LAYOUT_ABOVE,
            ATTR_LAYOUT_BELOW,
            ATTR_LAYOUT_TO_LEFT_OF,
            ATTR_LAYOUT_TO_RIGHT_OF,
            ATTR_LAYOUT_TO_START_OF,
            ATTR_LAYOUT_TO_END_OF,
            ATTR_LAYOUT_ALIGN_TOP,
            ATTR_LAYOUT_ALIGN_BOTTOM,
            ATTR_LAYOUT_ALIGN_LEFT,
            ATTR_LAYOUT_ALIGN_RIGHT,
            ATTR_LAYOUT_ALIGN_START,
            ATTR_LAYOUT_ALIGN_END,
            ATTR_LAYOUT_ALIGN_BASELINE,
            ATTR_LAYOUT_ALIGN_PARENT_TOP,
            ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
            ATTR_LAYOUT_ALIGN_PARENT_LEFT,
            ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
            ATTR_LAYOUT_ALIGN_PARENT_START,
            ATTR_LAYOUT_ALIGN_PARENT_END,
            ATTR_LAYOUT_CENTER_IN_PARENT,
            ATTR_LAYOUT_CENTER_HORIZONTAL,
            ATTR_LAYOUT_CENTER_VERTICAL,
            ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING
    ));

    // Layout params specific to GridLayout
    private static final Set<String> GRID_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            ATTR_LAYOUT_ROW,
            ATTR_LAYOUT_ROW_SPAN,
            ATTR_LAYOUT_COLUMN,
            ATTR_LAYOUT_COLUMN_SPAN,
            ATTR_LAYOUT_GRAVITY
    ));

    // Layout params specific to TableRow
    private static final Set<String> TABLE_ROW_PARAMS = new HashSet<>(Arrays.asList(
            ATTR_LAYOUT_COLUMN,
            ATTR_LAYOUT_SPAN,
            ATTR_LAYOUT_GRAVITY
    ));

    // Layout params specific to AbsoluteLayout
    private static final Set<String> ABSOLUTE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            ATTR_LAYOUT_X,
            ATTR_LAYOUT_Y
    ));

    // Layout params specific to FrameLayout
    private static final Set<String> FRAME_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            ATTR_LAYOUT_GRAVITY
    ));

    /**
     * Map from layout tag name to the set of layout params that are valid for children
     * of that layout (in addition to COMMON_PARAMS and MARGIN_PARAMS).
     */
    private static final Map<String, Set<String>> LAYOUT_PARAMS;

    static {
        LAYOUT_PARAMS = new HashMap<>();
        LAYOUT_PARAMS.put(LINEAR_LAYOUT, LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("android.widget.LinearLayout", LINEAR_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put(RELATIVE_LAYOUT, RELATIVE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("android.widget.RelativeLayout", RELATIVE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put(GRID_LAYOUT, GRID_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("android.widget.GridLayout", GRID_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put(TABLE_LAYOUT, TABLE_ROW_PARAMS);
        LAYOUT_PARAMS.put("android.widget.TableLayout", TABLE_ROW_PARAMS);
        LAYOUT_PARAMS.put(TABLE_ROW, TABLE_ROW_PARAMS);
        LAYOUT_PARAMS.put("android.widget.TableRow", TABLE_ROW_PARAMS);
        LAYOUT_PARAMS.put(FRAME_LAYOUT, FRAME_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("android.widget.FrameLayout", FRAME_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put(GRID_VIEW, GRID_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("android.widget.GridView", GRID_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("AbsoluteLayout", ABSOLUTE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("android.widget.AbsoluteLayout", ABSOLUTE_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("ScrollView", FRAME_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("android.widget.ScrollView", FRAME_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("HorizontalScrollView", FRAME_LAYOUT_PARAMS);
        LAYOUT_PARAMS.put("android.widget.HorizontalScrollView", FRAME_LAYOUT_PARAMS);
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
        // Get the parent element to determine what layout params are valid
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Root element or no parent - no layout params to validate
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        // Get the valid params for this parent layout
        Set<String> validSpecificParams = LAYOUT_PARAMS.get(parentTag);

        // Check all attributes of the element
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            String namespace = attr.getNamespaceURI();

            // Only check android namespace attributes that start with "layout_"
            if (!ANDROID_URI.equals(namespace) || name == null || !name.startsWith("layout_")) {
                continue;
            }

            // Skip common params (width, height) - always valid
            if (COMMON_PARAMS.contains(name)) {
                continue;
            }

            // Skip margin params - valid for most layouts that extend ViewGroup.MarginLayoutParams
            if (MARGIN_PARAMS.contains(name)) {
                continue;
            }

            // If we don't know the parent layout, we can't validate
            if (validSpecificParams == null) {
                // Unknown parent layout - skip validation
                continue;
            }

            // Check if this layout param is valid for the parent
            if (!validSpecificParams.contains(name)) {
                // Report the issue
                context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        String.format(
                                "Invalid layout param in a `%1$s`: `%2$s`",
                                parentTag,
                                name));
            }
        }
    }
}