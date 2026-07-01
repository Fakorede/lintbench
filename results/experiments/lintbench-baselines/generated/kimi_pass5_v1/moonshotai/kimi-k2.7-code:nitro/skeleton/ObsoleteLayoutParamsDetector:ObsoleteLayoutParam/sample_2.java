package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The layout parameters that a view accepts are defined by the `LayoutParams` "
                            + "class of its parent. If the parent does not define a particular "
                            + "`layout_` attribute, the attribute is ignored at runtime, wastes "
                            + "attribute processing, and should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LAYOUT_PREFIX = "layout_";

    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_LAYOUT_MARGIN = "layout_margin";
    private static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop";
    private static final String ATTR_LAYOUT_MARGIN_BOTTOM = "layout_marginBottom";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    private static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";
    private static final String ATTR_LAYOUT_MARGIN_VERTICAL = "layout_marginVertical";

    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String ATTR_LAYOUT_COLUMN = "layout_column";
    private static final String ATTR_LAYOUT_COLUMN_SPAN = "layout_columnSpan";
    private static final String ATTR_LAYOUT_ROW = "layout_row";
    private static final String ATTR_LAYOUT_ROW_SPAN = "layout_rowSpan";
    private static final String ATTR_LAYOUT_X = "layout_x";
    private static final String ATTR_LAYOUT_Y = "layout_y";

    private static final String ATTR_LAYOUT_ALIGN_BASELINE = "layout_alignBaseline";
    private static final String ATTR_LAYOUT_ALIGN_BOTTOM = "layout_alignBottom";
    private static final String ATTR_LAYOUT_ALIGN_END = "layout_alignEnd";
    private static final String ATTR_LAYOUT_ALIGN_LEFT = "layout_alignLeft";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_BOTTOM = "layout_alignParentBottom";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_END = "layout_alignParentEnd";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_LEFT = "layout_alignParentLeft";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_RIGHT = "layout_alignParentRight";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_START = "layout_alignParentStart";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_TOP = "layout_alignParentTop";
    private static final String ATTR_LAYOUT_ALIGN_RIGHT = "layout_alignRight";
    private static final String ATTR_LAYOUT_ALIGN_START = "layout_alignStart";
    private static final String ATTR_LAYOUT_ALIGN_TOP = "layout_alignTop";
    private static final String ATTR_LAYOUT_ALIGN_WITH_PARENT_IF_MISSING =
            "layout_alignWithParentIfMissing";
    private static final String ATTR_LAYOUT_BELOW = "layout_below";
    private static final String ATTR_LAYOUT_ABOVE = "layout_above";
    private static final String ATTR_LAYOUT_CENTER_HORIZONTAL = "layout_centerHorizontal";
    private static final String ATTR_LAYOUT_CENTER_IN_PARENT = "layout_centerInParent";
    private static final String ATTR_LAYOUT_CENTER_VERTICAL = "layout_centerVertical";
    private static final String ATTR_LAYOUT_TO_END_OF = "layout_toEndOf";
    private static final String ATTR_LAYOUT_TO_LEFT_OF = "layout_toLeftOf";
    private static final String ATTR_LAYOUT_TO_RIGHT_OF = "layout_toRightOf";
    private static final String ATTR_LAYOUT_TO_START_OF = "layout_toStartOf";

    private static final String ATTR_LAYOUT_ANCHOR = "layout_anchor";
    private static final String ATTR_LAYOUT_ANCHOR_GRAVITY = "layout_anchorGravity";
    private static final String ATTR_LAYOUT_BEHAVIOR = "layout_behavior";
    private static final String ATTR_LAYOUT_DODGE_INSET_EDGES = "layout_dodgeInsetEdges";
    private static final String ATTR_LAYOUT_INSET_EDGE = "layout_insetEdge";
    private static final String ATTR_LAYOUT_KEYLINE = "layout_keyline";

    private static final String ATTR_LAYOUT_SCROLL_FLAGS = "layout_scrollFlags";
    private static final String ATTR_LAYOUT_SCROLL_EFFECT = "layout_scrollEffect";
    private static final String ATTR_LAYOUT_COLLAPSE_MODE = "layout_collapseMode";
    private static final String ATTR_LAYOUT_COLLAPSE_PARALLAX_MULTIPLIER =
            "layout_collapseParallaxMultiplier";

    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";
    private static final String TAG_FRAME_LAYOUT = "FrameLayout";
    private static final String TAG_RELATIVE_LAYOUT = "RelativeLayout";
    private static final String TAG_GRID_LAYOUT = "GridLayout";
    private static final String TAG_TABLE_LAYOUT = "TableLayout";
    private static final String TAG_TABLE_ROW = "TableRow";
    private static final String TAG_ABSOLUTE_LAYOUT = "AbsoluteLayout";
    private static final String TAG_RADIO_GROUP = "RadioGroup";
    private static final String TAG_DRAWER_LAYOUT = "DrawerLayout";
    private static final String TAG_SLIDING_PANE_LAYOUT = "SlidingPaneLayout";
    private static final String TAG_TOOLBAR = "Toolbar";
    private static final String TAG_COORDINATOR_LAYOUT = "CoordinatorLayout";
    private static final String TAG_APP_BAR_LAYOUT = "AppBarLayout";
    private static final String TAG_COLLAPSING_TOOLBAR_LAYOUT = "CollapsingToolbarLayout";

    private static final String TAG_LINEAR_LAYOUT_COMPAT =
            "android.support.v7.widget.LinearLayoutCompat";
    private static final String TAG_LINEAR_LAYOUT_COMPAT_ANDROIDX =
            "androidx.appcompat.widget.LinearLayoutCompat";

    private static final String TAG_DRAWER_LAYOUT_SUPPORT =
            "android.support.v4.widget.DrawerLayout";
    private static final String TAG_DRAWER_LAYOUT_ANDROIDX =
            "androidx.drawerlayout.widget.DrawerLayout";

    private static final String TAG_SLIDING_PANE_LAYOUT_SUPPORT =
            "android.support.v4.widget.SlidingPaneLayout";
    private static final String TAG_SLIDING_PANE_LAYOUT_ANDROIDX =
            "androidx.slidingpanelayout.widget.SlidingPaneLayout";

    private static final String TAG_GRID_LAYOUT_V7 = "android.support.v7.widget.GridLayout";
    private static final String TAG_GRID_LAYOUT_ANDROIDX = "androidx.gridlayout.widget.GridLayout";

    private static final String TAG_TOOLBAR_SUPPORT = "android.support.v7.widget.Toolbar";
    private static final String TAG_TOOLBAR_ANDROIDX = "androidx.appcompat.widget.Toolbar";

    private static final String TAG_COORDINATOR_LAYOUT_SUPPORT =
            "android.support.design.widget.CoordinatorLayout";
    private static final String TAG_COORDINATOR_LAYOUT_ANDROIDX =
            "com.google.android.material.coordinatorlayout.CoordinatorLayout";

    private static final String TAG_APP_BAR_LAYOUT_SUPPORT =
            "android.support.design.widget.AppBarLayout";
    private static final String TAG_APP_BAR_LAYOUT_ANDROIDX =
            "com.google.android.material.appbar.AppBarLayout";

    private static final String TAG_COLLAPSING_TOOLBAR_LAYOUT_SUPPORT =
            "android.support.design.widget.CollapsingToolbarLayout";
    private static final String TAG_COLLAPSING_TOOLBAR_LAYOUT_ANDROIDX =
            "com.google.android.material.appbar.CollapsingToolbarLayout";

    private static final Set<String> BASE_VALID_ATTRS = new HashSet<>();
    private static final Map<String, Set<String>> VALID_LAYOUT_PARAMS = new HashMap<>();
    private static final Set<String> ALL_LAYOUT_PARAMS = new HashSet<>();

    static {
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_WIDTH);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_HEIGHT);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_MARGIN);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_MARGIN_LEFT);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_MARGIN_RIGHT);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_MARGIN_TOP);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_MARGIN_BOTTOM);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_MARGIN_START);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_MARGIN_END);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_MARGIN_HORIZONTAL);
        BASE_VALID_ATTRS.add(ATTR_LAYOUT_MARGIN_VERTICAL);

        addLayout(TAG_LINEAR_LAYOUT, ATTR_LAYOUT_WEIGHT, ATTR_LAYOUT_GRAVITY);
        addLayout(TAG_FRAME_LAYOUT, ATTR_LAYOUT_GRAVITY);
        addLayout(
                TAG_RELATIVE_LAYOUT,
                ATTR_LAYOUT_ALIGN_BASELINE,
                ATTR_LAYOUT_ALIGN_BOTTOM,
                ATTR_LAYOUT_ALIGN_END,
                ATTR_LAYOUT_ALIGN_LEFT,
                ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
                ATTR_LAYOUT_ALIGN_PARENT_END,
                ATTR_LAYOUT_ALIGN_PARENT_LEFT,
                ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
                ATTR_LAYOUT_ALIGN_PARENT_START,
                ATTR_LAYOUT_ALIGN_PARENT_TOP,
                ATTR_LAYOUT_ALIGN_RIGHT,
                ATTR_LAYOUT_ALIGN_START,
                ATTR_LAYOUT_ALIGN_TOP,
                ATTR_LAYOUT_ALIGN_WITH_PARENT_IF_MISSING,
                ATTR_LAYOUT_BELOW,
                ATTR_LAYOUT_ABOVE,
                ATTR_LAYOUT_CENTER_HORIZONTAL,
                ATTR_LAYOUT_CENTER_IN_PARENT,
                ATTR_LAYOUT_CENTER_VERTICAL,
                ATTR_LAYOUT_TO_END_OF,
                ATTR_LAYOUT_TO_LEFT_OF,
                ATTR_LAYOUT_TO_RIGHT_OF,
                ATTR_LAYOUT_TO_START_OF);
        addLayout(
                TAG_GRID_LAYOUT,
                ATTR_LAYOUT_COLUMN,
                ATTR_LAYOUT_COLUMN_SPAN,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_ROW,
                ATTR_LAYOUT_ROW_SPAN);
        addLayout(TAG_TABLE_LAYOUT);
        addLayout(
                TAG_TABLE_ROW,
                ATTR_LAYOUT_COLUMN,
                ATTR_LAYOUT_SPAN,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_WEIGHT);
        addLayout(TAG_RADIO_GROUP, ATTR_LAYOUT_WEIGHT, ATTR_LAYOUT_GRAVITY);
        addLayout(TAG_DRAWER_LAYOUT, ATTR_LAYOUT_GRAVITY);
        addLayout(TAG_SLIDING_PANE_LAYOUT, ATTR_LAYOUT_WEIGHT);
        addLayout(TAG_TOOLBAR, ATTR_LAYOUT_GRAVITY);

        addLayout(
                TAG_COORDINATOR_LAYOUT,
                ATTR_LAYOUT_ANCHOR,
                ATTR_LAYOUT_ANCHOR_GRAVITY,
                ATTR_LAYOUT_BEHAVIOR,
                ATTR_LAYOUT_DODGE_INSET_EDGES,
                ATTR_LAYOUT_INSET_EDGE,
                ATTR_LAYOUT_KEYLINE);
        addLayout(
                TAG_APP_BAR_LAYOUT,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_WEIGHT,
                ATTR_LAYOUT_SCROLL_FLAGS,
                ATTR_LAYOUT_SCROLL_EFFECT);
        addLayout(
                TAG_COLLAPSING_TOOLBAR_LAYOUT,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_COLLAPSE_MODE,
                ATTR_LAYOUT_COLLAPSE_PARALLAX_MULTIPLIER);

        addLayout(TAG_LINEAR_LAYOUT_COMPAT, ATTR_LAYOUT_WEIGHT, ATTR_LAYOUT_GRAVITY);
        addLayout(TAG_LINEAR_LAYOUT_COMPAT_ANDROIDX, ATTR_LAYOUT_WEIGHT, ATTR_LAYOUT_GRAVITY);

        addLayout(TAG_DRAWER_LAYOUT_SUPPORT, ATTR_LAYOUT_GRAVITY);
        addLayout(TAG_DRAWER_LAYOUT_ANDROIDX, ATTR_LAYOUT_GRAVITY);

        addLayout(TAG_SLIDING_PANE_LAYOUT_SUPPORT, ATTR_LAYOUT_WEIGHT);
        addLayout(TAG_SLIDING_PANE_LAYOUT_ANDROIDX, ATTR_LAYOUT_WEIGHT);

        addLayout(
                TAG_GRID_LAYOUT_V7,
                ATTR_LAYOUT_COLUMN,
                ATTR_LAYOUT_COLUMN_SPAN,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_ROW,
                ATTR_LAYOUT_ROW_SPAN);
        addLayout(
                TAG_GRID_LAYOUT_ANDROIDX,
                ATTR_LAYOUT_COLUMN,
                ATTR_LAYOUT_COLUMN_SPAN,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_ROW,
                ATTR_LAYOUT_ROW_SPAN);

        addLayout(TAG_TOOLBAR_SUPPORT, ATTR_LAYOUT_GRAVITY);
        addLayout(TAG_TOOLBAR_ANDROIDX, ATTR_LAYOUT_GRAVITY);

        addLayout(
                TAG_COORDINATOR_LAYOUT_SUPPORT,
                ATTR_LAYOUT_ANCHOR,
                ATTR_LAYOUT_ANCHOR_GRAVITY,
                ATTR_LAYOUT_BEHAVIOR,
                ATTR_LAYOUT_DODGE_INSET_EDGES,
                ATTR_LAYOUT_INSET_EDGE,
                ATTR_LAYOUT_KEYLINE);
        addLayout(
                TAG_COORDINATOR_LAYOUT_ANDROIDX,
                ATTR_LAYOUT_ANCHOR,
                ATTR_LAYOUT_ANCHOR_GRAVITY,
                ATTR_LAYOUT_BEHAVIOR,
                ATTR_LAYOUT_DODGE_INSET_EDGES,
                ATTR_LAYOUT_INSET_EDGE,
                ATTR_LAYOUT_KEYLINE);

        addLayout(
                TAG_APP_BAR_LAYOUT_SUPPORT,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_WEIGHT,
                ATTR_LAYOUT_SCROLL_FLAGS,
                ATTR_LAYOUT_SCROLL_EFFECT);
        addLayout(
                TAG_APP_BAR_LAYOUT_ANDROIDX,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_WEIGHT,
                ATTR_LAYOUT_SCROLL_FLAGS,
                ATTR_LAYOUT_SCROLL_EFFECT);

        addLayout(
                TAG_COLLAPSING_TOOLBAR_LAYOUT_SUPPORT,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_COLLAPSE_MODE,
                ATTR_LAYOUT_COLLAPSE_PARALLAX_MULTIPLIER);
        addLayout(
                TAG_COLLAPSING_TOOLBAR_LAYOUT_ANDROIDX,
                ATTR_LAYOUT_GRAVITY,
                ATTR_LAYOUT_COLLAPSE_MODE,
                ATTR_LAYOUT_COLLAPSE_PARALLAX_MULTIPLIER);

        Set<String> absolute = new HashSet<>();
        absolute.add(ATTR_LAYOUT_WIDTH);
        absolute.add(ATTR_LAYOUT_HEIGHT);
        absolute.add(ATTR_LAYOUT_X);
        absolute.add(ATTR_LAYOUT_Y);
        VALID_LAYOUT_PARAMS.put(TAG_ABSOLUTE_LAYOUT, absolute);

        ALL_LAYOUT_PARAMS.addAll(BASE_VALID_ATTRS);
        for (Set<String> attrs : VALID_LAYOUT_PARAMS.values()) {
            ALL_LAYOUT_PARAMS.addAll(attrs);
        }
    }

    private static final String ATTR_LAYOUT_SPAN = "layout_span";

    private static void addLayout(String tag, String... extras) {
        Set<String> attrs = new HashSet<>(BASE_VALID_ATTRS);
        Collections.addAll(attrs, extras);
        VALID_LAYOUT_PARAMS.put(tag, attrs);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL_LAYOUT_PARAMS;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null
                || !name.startsWith(LAYOUT_PREFIX)
                || !ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String parentTag = getParentTag(attribute);
        if (parentTag == null) {
            return;
        }

        Set<String> valid = VALID_LAYOUT_PARAMS.get(parentTag);
        if (valid == null) {
            return;
        }

        if (!valid.contains(name)) {
            Location location = context.getLocation(attribute);
            String message =
                    String.format("Invalid layout param in a %1$s: %2$s", parentTag, name);
            context.report(ISSUE, attribute, location, message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {}

    @Override
    public void afterCheckRootProject(@NonNull Context context) {}

    private static String getParentTag(@NonNull Attr attribute) {
        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return null;
        }
        Node parent = owner.getParentNode();
        if (!(parent instanceof Element)) {
            return null;
        }
        return ((Element) parent).getTagName();
    }
}