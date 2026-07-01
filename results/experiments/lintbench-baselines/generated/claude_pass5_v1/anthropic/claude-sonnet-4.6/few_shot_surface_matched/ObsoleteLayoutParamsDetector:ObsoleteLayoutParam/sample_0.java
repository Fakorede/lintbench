package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
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
                    "The given `layout_param` is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    // Layout params defined by specific parent layouts
    private static final Map<String, Set<String>> LAYOUT_PARAMS = new HashMap<>();

    // Params defined by ViewGroup (available to all layouts)
    private static final Set<String> VIEW_GROUP_PARAMS = new HashSet<>(Arrays.asList(
            "layout_width",
            "layout_height"
    ));

    // Params defined by ViewGroup.MarginLayoutParams
    private static final Set<String> MARGIN_PARAMS = new HashSet<>(Arrays.asList(
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

    static {
        // LinearLayout params
        Set<String> linearLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        linearLayoutParams.addAll(MARGIN_PARAMS);
        linearLayoutParams.add("layout_weight");
        linearLayoutParams.add("layout_gravity");
        LAYOUT_PARAMS.put("LinearLayout", linearLayoutParams);

        // RelativeLayout params
        Set<String> relativeLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        relativeLayoutParams.addAll(MARGIN_PARAMS);
        relativeLayoutParams.add("layout_above");
        relativeLayoutParams.add("layout_below");
        relativeLayoutParams.add("layout_toLeftOf");
        relativeLayoutParams.add("layout_toRightOf");
        relativeLayoutParams.add("layout_toStartOf");
        relativeLayoutParams.add("layout_toEndOf");
        relativeLayoutParams.add("layout_alignTop");
        relativeLayoutParams.add("layout_alignBottom");
        relativeLayoutParams.add("layout_alignLeft");
        relativeLayoutParams.add("layout_alignRight");
        relativeLayoutParams.add("layout_alignStart");
        relativeLayoutParams.add("layout_alignEnd");
        relativeLayoutParams.add("layout_alignParentTop");
        relativeLayoutParams.add("layout_alignParentBottom");
        relativeLayoutParams.add("layout_alignParentLeft");
        relativeLayoutParams.add("layout_alignParentRight");
        relativeLayoutParams.add("layout_alignParentStart");
        relativeLayoutParams.add("layout_alignParentEnd");
        relativeLayoutParams.add("layout_alignBaseline");
        relativeLayoutParams.add("layout_centerInParent");
        relativeLayoutParams.add("layout_centerHorizontal");
        relativeLayoutParams.add("layout_centerVertical");
        LAYOUT_PARAMS.put("RelativeLayout", relativeLayoutParams);

        // FrameLayout params
        Set<String> frameLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        frameLayoutParams.addAll(MARGIN_PARAMS);
        frameLayoutParams.add("layout_gravity");
        LAYOUT_PARAMS.put("FrameLayout", frameLayoutParams);

        // GridLayout params
        Set<String> gridLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        gridLayoutParams.addAll(MARGIN_PARAMS);
        gridLayoutParams.add("layout_row");
        gridLayoutParams.add("layout_rowSpan");
        gridLayoutParams.add("layout_rowWeight");
        gridLayoutParams.add("layout_column");
        gridLayoutParams.add("layout_columnSpan");
        gridLayoutParams.add("layout_columnWeight");
        gridLayoutParams.add("layout_gravity");
        LAYOUT_PARAMS.put("GridLayout", gridLayoutParams);

        // TableLayout / TableRow params
        Set<String> tableLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        tableLayoutParams.addAll(MARGIN_PARAMS);
        tableLayoutParams.add("layout_gravity");
        tableLayoutParams.add("layout_column");
        tableLayoutParams.add("layout_span");
        LAYOUT_PARAMS.put("TableLayout", tableLayoutParams);
        LAYOUT_PARAMS.put("TableRow", tableLayoutParams);

        // ConstraintLayout params
        Set<String> constraintLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        constraintLayoutParams.addAll(MARGIN_PARAMS);
        constraintLayoutParams.add("layout_constraintLeft_toLeftOf");
        constraintLayoutParams.add("layout_constraintLeft_toRightOf");
        constraintLayoutParams.add("layout_constraintRight_toLeftOf");
        constraintLayoutParams.add("layout_constraintRight_toRightOf");
        constraintLayoutParams.add("layout_constraintTop_toTopOf");
        constraintLayoutParams.add("layout_constraintTop_toBottomOf");
        constraintLayoutParams.add("layout_constraintBottom_toTopOf");
        constraintLayoutParams.add("layout_constraintBottom_toBottomOf");
        constraintLayoutParams.add("layout_constraintStart_toStartOf");
        constraintLayoutParams.add("layout_constraintStart_toEndOf");
        constraintLayoutParams.add("layout_constraintEnd_toStartOf");
        constraintLayoutParams.add("layout_constraintEnd_toEndOf");
        constraintLayoutParams.add("layout_constraintBaseline_toBaselineOf");
        constraintLayoutParams.add("layout_constraintHorizontal_bias");
        constraintLayoutParams.add("layout_constraintVertical_bias");
        constraintLayoutParams.add("layout_constraintHorizontal_weight");
        constraintLayoutParams.add("layout_constraintVertical_weight");
        constraintLayoutParams.add("layout_constraintHorizontal_chainStyle");
        constraintLayoutParams.add("layout_constraintVertical_chainStyle");
        constraintLayoutParams.add("layout_constraintDimensionRatio");
        constraintLayoutParams.add("layout_constraintGuide_begin");
        constraintLayoutParams.add("layout_constraintGuide_end");
        constraintLayoutParams.add("layout_constraintGuide_percent");
        constraintLayoutParams.add("layout_constraintCircle");
        constraintLayoutParams.add("layout_constraintCircleRadius");
        constraintLayoutParams.add("layout_constraintCircleAngle");
        constraintLayoutParams.add("layout_constraintWidth_min");
        constraintLayoutParams.add("layout_constraintWidth_max");
        constraintLayoutParams.add("layout_constraintWidth_percent");
        constraintLayoutParams.add("layout_constraintHeight_min");
        constraintLayoutParams.add("layout_constraintHeight_max");
        constraintLayoutParams.add("layout_constraintHeight_percent");
        constraintLayoutParams.add("layout_goneMarginLeft");
        constraintLayoutParams.add("layout_goneMarginTop");
        constraintLayoutParams.add("layout_goneMarginRight");
        constraintLayoutParams.add("layout_goneMarginBottom");
        constraintLayoutParams.add("layout_goneMarginStart");
        constraintLayoutParams.add("layout_goneMarginEnd");
        constraintLayoutParams.add("layout_editor_absoluteX");
        constraintLayoutParams.add("layout_editor_absoluteY");
        LAYOUT_PARAMS.put("ConstraintLayout", constraintLayoutParams);
        LAYOUT_PARAMS.put("androidx.constraintlayout.widget.ConstraintLayout", constraintLayoutParams);

        // CoordinatorLayout params
        Set<String> coordinatorLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        coordinatorLayoutParams.addAll(MARGIN_PARAMS);
        coordinatorLayoutParams.add("layout_gravity");
        coordinatorLayoutParams.add("layout_behavior");
        coordinatorLayoutParams.add("layout_anchor");
        coordinatorLayoutParams.add("layout_anchorGravity");
        coordinatorLayoutParams.add("layout_keyline");
        coordinatorLayoutParams.add("layout_dodgeInsetEdges");
        coordinatorLayoutParams.add("layout_insetEdge");
        LAYOUT_PARAMS.put("CoordinatorLayout", coordinatorLayoutParams);
        LAYOUT_PARAMS.put("androidx.coordinatorlayout.widget.CoordinatorLayout", coordinatorLayoutParams);

        // AppBarLayout params
        Set<String> appBarLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        appBarLayoutParams.addAll(MARGIN_PARAMS);
        appBarLayoutParams.add("layout_scrollFlags");
        appBarLayoutParams.add("layout_scrollInterpolator");
        LAYOUT_PARAMS.put("AppBarLayout", appBarLayoutParams);
        LAYOUT_PARAMS.put("com.google.android.material.appbar.AppBarLayout", appBarLayoutParams);

        // CollapsingToolbarLayout params
        Set<String> collapsingParams = new HashSet<>(VIEW_GROUP_PARAMS);
        collapsingParams.addAll(MARGIN_PARAMS);
        collapsingParams.add("layout_collapseMode");
        collapsingParams.add("layout_collapseParallaxMultiplier");
        LAYOUT_PARAMS.put("CollapsingToolbarLayout", collapsingParams);
        LAYOUT_PARAMS.put("com.google.android.material.appbar.CollapsingToolbarLayout", collapsingParams);

        // DrawerLayout params
        Set<String> drawerLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        drawerLayoutParams.addAll(MARGIN_PARAMS);
        drawerLayoutParams.add("layout_gravity");
        drawerLayoutParams.add("layout_keyline");
        LAYOUT_PARAMS.put("DrawerLayout", drawerLayoutParams);
        LAYOUT_PARAMS.put("androidx.drawerlayout.widget.DrawerLayout", drawerLayoutParams);

        // RecyclerView (no special layout params beyond ViewGroup)
        Set<String> recyclerViewParams = new HashSet<>(VIEW_GROUP_PARAMS);
        recyclerViewParams.addAll(MARGIN_PARAMS);
        LAYOUT_PARAMS.put("RecyclerView", recyclerViewParams);
        LAYOUT_PARAMS.put("androidx.recyclerview.widget.RecyclerView", recyclerViewParams);

        // ScrollView / HorizontalScrollView
        Set<String> scrollViewParams = new HashSet<>(VIEW_GROUP_PARAMS);
        scrollViewParams.addAll(MARGIN_PARAMS);
        scrollViewParams.add("layout_gravity");
        LAYOUT_PARAMS.put("ScrollView", scrollViewParams);
        LAYOUT_PARAMS.put("HorizontalScrollView", scrollViewParams);

        // ViewPager
        Set<String> viewPagerParams = new HashSet<>(VIEW_GROUP_PARAMS);
        viewPagerParams.addAll(MARGIN_PARAMS);
        LAYOUT_PARAMS.put("ViewPager", viewPagerParams);
        LAYOUT_PARAMS.put("androidx.viewpager.widget.ViewPager", viewPagerParams);

        // AbsoluteLayout (deprecated)
        Set<String> absoluteLayoutParams = new HashSet<>(VIEW_GROUP_PARAMS);
        absoluteLayoutParams.add("layout_x");
        absoluteLayoutParams.add("layout_y");
        LAYOUT_PARAMS.put("AbsoluteLayout", absoluteLayoutParams);
    }

    // Pending issues to report after we've seen the whole file
    private final List<PendingIssue> mPendingIssues = new ArrayList<>();

    // Map from element to its parent tag name, built during visitElement
    private final Map<Element, String> mElementToParentTag = new HashMap<>();

    private static class PendingIssue {
        final XmlContext context;
        final Attr attribute;
        final Element element;

        PendingIssue(XmlContext context, Attr attribute, Element element) {
            this.context = context;
            this.attribute = attribute;
            this.element = element;
        }
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        // We want to visit all elements to build parent-child map
        return ALL;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        // We want to visit all layout_ attributes
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Record the parent tag for each child element
        Node parentNode = element.getParentNode();
        if (parentNode != null && parentNode.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parentNode;
            mElementToParentTag.put(element, parentElement.getTagName());
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            return;
        }

        // Only check layout_ attributes in the android namespace
        if (!localName.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)) {
            return;
        }

        // Must be in android namespace (or app namespace for things like ConstraintLayout)
        String namespace = attribute.getNamespaceURI();
        if (namespace == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String tagName = element.getTagName();

        // Skip root elements — they may be used by a parent layout that includes this file
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // This is the root element
            return;
        }

        // Skip <merge> and <include> tags
        if (VIEW_MERGE.equals(tagName) || VIEW_INCLUDE.equals(tagName)) {
            return;
        }

        // Queue for later processing once we've built the full element map
        mPendingIssues.add(new PendingIssue(context, attribute, element));
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        for (PendingIssue pending : mPendingIssues) {
            checkPendingIssue(pending);
        }
        mPendingIssues.clear();
        mElementToParentTag.clear();
    }

    private void checkPendingIssue(@NonNull PendingIssue pending) {
        XmlContext context = pending.context;
        Attr attribute = pending.attribute;
        Element element = pending.element;

        String localName = attribute.getLocalName();
        if (localName == null) {
            return;
        }

        // Get the parent tag
        String parentTag = mElementToParentTag.get(element);
        if (parentTag == null) {
            return;
        }

        // Skip if parent is merge or include
        if (VIEW_MERGE.equals(parentTag) || VIEW_INCLUDE.equals(parentTag)) {
            return;
        }

        // Normalize the parent tag to a simple name for lookup
        String simpleParentTag = getSimpleTagName(parentTag);

        Set<String> validParams = getValidParamsForLayout(parentTag, simpleParentTag);

        if (validParams == null) {
            // Unknown layout — we can't determine valid params, skip
            return;
        }

        if (!validParams.contains(localName)) {
            String message = String.format(
                    "Invalid layout param `%1$s` (target layout `%2$s`)",
                    localName, parentTag);

            LintFix fix = LintFix.create()
                    .name("Remove attribute")
                    .unset(attribute.getNamespaceURI(), localName)
                    .build();

            context.report(ISSUE, attribute, context.getLocation(attribute), message, fix);
        }
    }

    @Nullable
    private Set<String> getValidParamsForLayout(@NonNull String fullTag, @NonNull String simpleTag) {
        // Try full qualified name first
        Set<String> params = LAYOUT_PARAMS.get(fullTag);
        if (params != null) {
            return params;
        }

        // Try simple name
        params = LAYOUT_PARAMS.get(simpleTag);
        if (params != null) {
            return params;
        }

        // For layouts ending in "Layout", use a default set with margin params
        // since most custom layouts extend ViewGroup or a layout that supports margins
        if (simpleTag.endsWith("Layout")) {
            Set<String> defaultParams = new HashSet<>(VIEW_GROUP_PARAMS);
            defaultParams.addAll(MARGIN_PARAMS);
            defaultParams.add("layout_gravity");
            return defaultParams;
        }

        return null;
    }

    @NonNull
    private static String getSimpleTagName(@NonNull String tag) {
        int dotIndex = tag.lastIndexOf('.');
        if (dotIndex != -1) {
            return tag.substring(dotIndex + 1);
        }
        return tag;
    }
}