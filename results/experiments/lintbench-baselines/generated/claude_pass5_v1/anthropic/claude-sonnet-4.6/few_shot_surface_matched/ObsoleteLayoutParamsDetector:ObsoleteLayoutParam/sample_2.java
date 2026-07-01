package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given `layout_param` is not defined for the given layout, meaning it has"
                            + " no effect. This usually happens when you change the parent layout"
                            + " or move view code around without updating the layout params. This"
                            + " will cause useless attribute processing at runtime, and is"
                            + " misleading for others reading the layout so the parameter should"
                            + " be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    // Layout param attributes that are specific to certain parent layouts
    // Map from layout param attribute name to the set of parent layouts that support it
    private static final Map<String, Set<String>> PARAM_TO_LAYOUTS;
    // Layout param attributes valid in ALL ViewGroup layouts
    private static final Set<String> COMMON_PARAMS;

    static {
        COMMON_PARAMS = new HashSet<>(Arrays.asList(
                "layout_width",
                "layout_height",
                "layout_margin",
                "layout_marginLeft",
                "layout_marginRight",
                "layout_marginTop",
                "layout_marginBottom",
                "layout_marginStart",
                "layout_marginEnd",
                "layout_marginHorizontal",
                "layout_marginVertical"
        ));

        PARAM_TO_LAYOUTS = new HashMap<>();

        // LinearLayout params
        Set<String> linearLayoutParams = new HashSet<>(Arrays.asList(
                "layout_weight",
                "layout_gravity"
        ));
        for (String param : linearLayoutParams) {
            Set<String> layouts = new HashSet<>();
            layouts.add("LinearLayout");
            layouts.add("RadioGroup");
            PARAM_TO_LAYOUTS.put(param, layouts);
        }

        // RelativeLayout params
        String[] relativeParams = {
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
                "layout_alignParentTop",
                "layout_alignParentBottom",
                "layout_alignParentLeft",
                "layout_alignParentRight",
                "layout_alignParentStart",
                "layout_alignParentEnd",
                "layout_alignWithParentIfMissing",
                "layout_centerHorizontal",
                "layout_centerVertical",
                "layout_centerInParent"
        };
        for (String param : relativeParams) {
            Set<String> layouts = new HashSet<>();
            layouts.add("RelativeLayout");
            PARAM_TO_LAYOUTS.put(param, layouts);
        }

        // FrameLayout params - layout_gravity is also supported here
        Set<String> frameLayouts = PARAM_TO_LAYOUTS.computeIfAbsent("layout_gravity", k -> new HashSet<>());
        frameLayouts.add("FrameLayout");
        frameLayouts.add("ScrollView");
        frameLayouts.add("HorizontalScrollView");

        // GridLayout params
        String[] gridLayoutParams = {
                "layout_row",
                "layout_rowSpan",
                "layout_rowWeight",
                "layout_column",
                "layout_columnSpan",
                "layout_columnWeight",
                "layout_gravity"
        };
        for (String param : gridLayoutParams) {
            Set<String> layouts = PARAM_TO_LAYOUTS.computeIfAbsent(param, k -> new HashSet<>());
            layouts.add("GridLayout");
        }

        // TableLayout / TableRow params
        String[] tableParams = {
                "layout_column",
                "layout_span"
        };
        for (String param : tableParams) {
            Set<String> layouts = PARAM_TO_LAYOUTS.computeIfAbsent(param, k -> new HashSet<>());
            layouts.add("TableRow");
        }

        // layout_gravity also valid in TableRow children context (via LinearLayout inheritance)
        Set<String> gravityLayouts = PARAM_TO_LAYOUTS.computeIfAbsent("layout_gravity", k -> new HashSet<>());
        gravityLayouts.add("TableRow");
        gravityLayouts.add("TableLayout");

        // ConstraintLayout params
        String[] constraintParams = {
                "layout_constraintLeft_toLeftOf",
                "layout_constraintLeft_toRightOf",
                "layout_constraintRight_toLeftOf",
                "layout_constraintRight_toRightOf",
                "layout_constraintTop_toTopOf",
                "layout_constraintTop_toBottomOf",
                "layout_constraintBottom_toTopOf",
                "layout_constraintBottom_toBottomOf",
                "layout_constraintStart_toStartOf",
                "layout_constraintStart_toEndOf",
                "layout_constraintEnd_toStartOf",
                "layout_constraintEnd_toEndOf",
                "layout_constraintBaseline_toBaselineOf",
                "layout_constraintDimensionRatio",
                "layout_constraintHorizontal_bias",
                "layout_constraintVertical_bias",
                "layout_constraintHorizontal_chainStyle",
                "layout_constraintVertical_chainStyle",
                "layout_constraintHorizontal_weight",
                "layout_constraintVertical_weight",
                "layout_constraintWidth_default",
                "layout_constraintHeight_default",
                "layout_constraintWidth_min",
                "layout_constraintWidth_max",
                "layout_constraintHeight_min",
                "layout_constraintHeight_max",
                "layout_constraintWidth_percent",
                "layout_constraintHeight_percent",
                "layout_constraintCircle",
                "layout_constraintCircleRadius",
                "layout_constraintCircleAngle",
                "layout_editor_absoluteX",
                "layout_editor_absoluteY",
                "layout_goneMarginLeft",
                "layout_goneMarginTop",
                "layout_goneMarginRight",
                "layout_goneMarginBottom",
                "layout_goneMarginStart",
                "layout_goneMarginEnd"
        };
        for (String param : constraintParams) {
            Set<String> layouts = PARAM_TO_LAYOUTS.computeIfAbsent(param, k -> new HashSet<>());
            layouts.add("ConstraintLayout");
            layouts.add("android.support.constraint.ConstraintLayout");
            layouts.add("androidx.constraintlayout.widget.ConstraintLayout");
        }

        // CoordinatorLayout params
        String[] coordinatorParams = {
                "layout_behavior",
                "layout_anchor",
                "layout_anchorGravity",
                "layout_keyline",
                "layout_dodgeInsetEdges",
                "layout_insetEdge"
        };
        for (String param : coordinatorParams) {
            Set<String> layouts = PARAM_TO_LAYOUTS.computeIfAbsent(param, k -> new HashSet<>());
            layouts.add("CoordinatorLayout");
            layouts.add("android.support.design.widget.CoordinatorLayout");
            layouts.add("androidx.coordinatorlayout.widget.CoordinatorLayout");
        }

        // AppBarLayout params
        String[] appBarParams = {
                "layout_scrollFlags",
                "layout_scrollInterpolator"
        };
        for (String param : appBarParams) {
            Set<String> layouts = PARAM_TO_LAYOUTS.computeIfAbsent(param, k -> new HashSet<>());
            layouts.add("AppBarLayout");
            layouts.add("com.google.android.material.appbar.AppBarLayout");
            layouts.add("android.support.design.widget.AppBarLayout");
        }

        // CollapsingToolbarLayout params
        String[] collapsingParams = {
                "layout_collapseMode",
                "layout_collapseParallaxMultiplier"
        };
        for (String param : collapsingParams) {
            Set<String> layouts = PARAM_TO_LAYOUTS.computeIfAbsent(param, k -> new HashSet<>());
            layouts.add("CollapsingToolbarLayout");
            layouts.add("com.google.android.material.appbar.CollapsingToolbarLayout");
            layouts.add("android.support.design.widget.CollapsingToolbarLayout");
        }
    }

    // Pending reports: stored until afterCheckRootProject so we can potentially
    // suppress false positives caused by includes or merge tags
    private final List<PendingReport> mPendingReports = new ArrayList<>();

    // Track whether we've seen a merge or include tag in this file
    private boolean mHasMergeOrInclude = false;

    private static class PendingReport {
        final XmlContext context;
        final Attr attribute;
        final Location location;
        final String message;

        PendingReport(XmlContext context, Attr attribute, Location location, String message) {
            this.context = context;
            this.attribute = attribute;
            this.location = location;
            this.message = message;
        }
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList("merge", "include", "fragment", "requestFocus");
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        // Collect all layout_ attributes we know about
        Set<String> attrs = new HashSet<>(COMMON_PARAMS);
        attrs.addAll(PARAM_TO_LAYOUTS.keySet());
        // Also return a sentinel to catch all layout_ prefixed attrs
        // We'll filter in visitAttribute
        return Collections.unmodifiableList(new ArrayList<>(attrs));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("merge".equals(tag) || "include".equals(tag)) {
            mHasMergeOrInclude = true;
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            localName = attribute.getName();
        }

        // Only care about layout_ attributes
        if (!localName.startsWith("layout_")) {
            return;
        }

        // Common params are always valid in any ViewGroup
        if (COMMON_PARAMS.contains(localName)) {
            return;
        }

        // Get the parent element (the element this attribute belongs to)
        Element element = attribute.getOwnerElement();

        // Get the grandparent element (the layout container)
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // This is a root element or has no parent - layout params don't apply
            // unless it's a merge layout
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();

        // If parent is merge or include, we can't determine the real parent
        if ("merge".equals(parentTag) || "include".equals(parentTag) || "fragment".equals(parentTag)) {
            return;
        }

        // Get the simple name of the parent layout
        String parentSimpleName = getSimpleName(parentTag);

        // Check if this layout param is valid for the parent
        Set<String> validLayouts = PARAM_TO_LAYOUTS.get(localName);
        if (validLayouts == null) {
            // Unknown layout param - could be from a custom layout, skip
            return;
        }

        // Check if the parent is one of the valid layouts
        if (isValidParent(parentTag, parentSimpleName, validLayouts)) {
            return;
        }

        // Report the issue
        String message = String.format(
                "Invalid layout param `%1$s` (not defined for `%2$s`)",
                localName, parentSimpleName);

        Location location = context.getLocation(attribute);
        mPendingReports.add(new PendingReport(context, attribute, location, message));
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (!mHasMergeOrInclude) {
            for (PendingReport report : mPendingReports) {
                report.context.report(ISSUE, report.attribute, report.location, report.message);
            }
        }
        mPendingReports.clear();
        mHasMergeOrInclude = false;
    }

    private static boolean isValidParent(String parentTag, String parentSimpleName,
            Set<String> validLayouts) {
        // Check full tag name
        if (validLayouts.contains(parentTag)) {
            return true;
        }
        // Check simple name
        if (validLayouts.contains(parentSimpleName)) {
            return true;
        }
        return false;
    }

    private static String getSimpleName(String tag) {
        int lastDot = tag.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < tag.length() - 1) {
            return tag.substring(lastDot + 1);
        }
        return tag;
    }
}