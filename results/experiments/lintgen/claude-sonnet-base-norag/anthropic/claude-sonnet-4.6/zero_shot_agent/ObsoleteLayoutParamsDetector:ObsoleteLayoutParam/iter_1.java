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
 * Checks for obsolete layout params (params that are defined for a layout that
 * is not the parent of the element defining the params).
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

    /**
     * Map from layout attribute local name to the set of parent layout simple names
     * that define this attribute.
     */
    private static final Map<String, Set<String>> PARAM_TO_LAYOUTS = new HashMap<>();

    /**
     * Set of all known layout container simple names. We only report errors
     * when the parent is a known layout to avoid false positives with custom views.
     */
    private static final Set<String> KNOWN_LAYOUTS = new HashSet<>();

    static {
        // AbsoluteLayout params
        registerParam("layout_x", "AbsoluteLayout");
        registerParam("layout_y", "AbsoluteLayout");

        // LinearLayout / RadioGroup params
        registerParam("layout_weight", "LinearLayout", "RadioGroup");
        // layout_gravity is valid in LinearLayout, GridLayout, FrameLayout
        registerParam("layout_gravity",
                "LinearLayout", "RadioGroup",
                "GridLayout",
                "FrameLayout",
                "ScrollView", "HorizontalScrollView");

        // RelativeLayout params
        registerParam("layout_above", "RelativeLayout");
        registerParam("layout_below", "RelativeLayout");
        registerParam("layout_toLeftOf", "RelativeLayout");
        registerParam("layout_toRightOf", "RelativeLayout");
        registerParam("layout_toStartOf", "RelativeLayout");
        registerParam("layout_toEndOf", "RelativeLayout");
        registerParam("layout_alignTop", "RelativeLayout");
        registerParam("layout_alignBottom", "RelativeLayout");
        registerParam("layout_alignLeft", "RelativeLayout");
        registerParam("layout_alignRight", "RelativeLayout");
        registerParam("layout_alignStart", "RelativeLayout");
        registerParam("layout_alignEnd", "RelativeLayout");
        registerParam("layout_alignBaseline", "RelativeLayout");
        registerParam("layout_alignParentTop", "RelativeLayout");
        registerParam("layout_alignParentBottom", "RelativeLayout");
        registerParam("layout_alignParentLeft", "RelativeLayout");
        registerParam("layout_alignParentRight", "RelativeLayout");
        registerParam("layout_alignParentStart", "RelativeLayout");
        registerParam("layout_alignParentEnd", "RelativeLayout");
        registerParam("layout_centerInParent", "RelativeLayout");
        registerParam("layout_centerHorizontal", "RelativeLayout");
        registerParam("layout_centerVertical", "RelativeLayout");

        // GridLayout params
        registerParam("layout_row", "GridLayout");
        registerParam("layout_rowSpan", "GridLayout");
        registerParam("layout_rowWeight", "GridLayout");
        registerParam("layout_column", "GridLayout", "TableRow");
        registerParam("layout_columnSpan", "GridLayout");
        registerParam("layout_columnWeight", "GridLayout");

        // TableRow params
        registerParam("layout_span", "TableRow");

        // Known layouts
        KNOWN_LAYOUTS.addAll(Arrays.asList(
                "LinearLayout",
                "RelativeLayout",
                "FrameLayout",
                "TableLayout",
                "TableRow",
                "GridLayout",
                "AbsoluteLayout",
                "ScrollView",
                "HorizontalScrollView",
                "ListView",
                "GridView",
                "ExpandableListView",
                "RadioGroup",
                "SlidingDrawer",
                "ViewAnimator",
                "ViewFlipper",
                "ViewSwitcher",
                "TextSwitcher",
                "ImageSwitcher",
                "AdapterViewFlipper",
                "StackView",
                "CoordinatorLayout",
                "AppBarLayout",
                "CollapsingToolbarLayout",
                "ConstraintLayout",
                "DrawerLayout"
        ));
    }

    private static void registerParam(String param, String... layouts) {
        Set<String> layoutSet = PARAM_TO_LAYOUTS.computeIfAbsent(param, k -> new HashSet<>());
        for (String layout : layouts) {
            // Store simple name
            int dotIndex = layout.lastIndexOf('.');
            String simpleName = dotIndex >= 0 ? layout.substring(dotIndex + 1) : layout;
            layoutSet.add(simpleName);
        }
    }

    /** Constructs a new {@link ObsoleteLayoutParamsDetector} */
    public ObsoleteLayoutParamsDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return PARAM_TO_LAYOUTS.keySet();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            if (name == null) {
                return;
            }
            // Strip namespace prefix if present
            int colon = name.indexOf(':');
            if (colon >= 0) {
                name = name.substring(colon + 1);
            }
        }

        Set<String> validParents = PARAM_TO_LAYOUTS.get(name);
        if (validParents == null || validParents.isEmpty()) {
            return;
        }

        // Get the element that owns this attribute
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Get the parent element (the layout container)
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Root element - layout params used by including layout, skip
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();
        if (parentTag == null) {
            return;
        }

        // Handle merge/include/fragment - can't determine actual parent
        if (isMergeOrInclude(parentTag)) {
            return;
        }

        // Get the simple name of the parent tag
        String parentSimpleName = parentTag;
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex >= 0) {
            parentSimpleName = parentTag.substring(dotIndex + 1);
        }

        // Check if the parent is a valid container for this layout param
        if (validParents.contains(parentSimpleName) || validParents.contains(parentTag)) {
            return;
        }

        // Only report for known layouts to avoid false positives with custom views
        if (!KNOWN_LAYOUTS.contains(parentSimpleName) && !KNOWN_LAYOUTS.contains(parentTag)) {
            return;
        }

        context.report(ISSUE, attribute, context.getLocation(attribute),
                String.format("Invalid layout param `%1$s` (not defined by parent layout `%2$s`)",
                        name, parentTag));
    }

    /**
     * Returns true if the tag is a merge, include, or fragment tag where
     * we cannot statically determine the actual parent layout.
     */
    private static boolean isMergeOrInclude(@NonNull String tag) {
        return tag.equals("merge") || tag.equals("include") || tag.equals("fragment")
                || tag.equals("requestFocus") || tag.equals("tag");
    }
}