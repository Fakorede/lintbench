package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
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
                    "The given layout param is not defined for the given layout, meaning it has no"
                            + " effect. This usually happens when you change the parent layout or"
                            + " move view code around without updating the layout params. This will"
                            + " cause useless attribute processing at runtime, and is misleading"
                            + " for others reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    // Layout params that are only valid in specific parent layouts
    // Map from attribute local name to the set of parent layouts that support it
    private static final Map<String, Set<String>> PARAM_TO_PARENTS;

    // Layout params that are valid in any ViewGroup (defined by ViewGroup.LayoutParams)
    private static final Set<String> VIEWGROUP_PARAMS;

    // Layout params that are valid in any ViewGroup.MarginLayoutParams subclass
    private static final Set<String> MARGIN_PARAMS;

    static {
        VIEWGROUP_PARAMS = new HashSet<>(Arrays.asList(
                "layout_width",
                "layout_height"
        ));

        MARGIN_PARAMS = new HashSet<>(Arrays.asList(
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

        PARAM_TO_PARENTS = new HashMap<>();

        // LinearLayout params
        addParam("layout_weight", "LinearLayout");
        addParam("layout_gravity",
                "LinearLayout", "FrameLayout", "ScrollView", "HorizontalScrollView");

        // RelativeLayout params
        addParam("layout_alignWithParentIfMissing", "RelativeLayout");
        addParam("layout_toLeftOf", "RelativeLayout");
        addParam("layout_toRightOf", "RelativeLayout");
        addParam("layout_above", "RelativeLayout");
        addParam("layout_below", "RelativeLayout");
        addParam("layout_alignBaseline", "RelativeLayout");
        addParam("layout_alignLeft", "RelativeLayout");
        addParam("layout_alignTop", "RelativeLayout");
        addParam("layout_alignRight", "RelativeLayout");
        addParam("layout_alignBottom", "RelativeLayout");
        addParam("layout_alignParentLeft", "RelativeLayout");
        addParam("layout_alignParentTop", "RelativeLayout");
        addParam("layout_alignParentRight", "RelativeLayout");
        addParam("layout_alignParentBottom", "RelativeLayout");
        addParam("layout_centerInParent", "RelativeLayout");
        addParam("layout_centerHorizontal", "RelativeLayout");
        addParam("layout_centerVertical", "RelativeLayout");
        addParam("layout_toStartOf", "RelativeLayout");
        addParam("layout_toEndOf", "RelativeLayout");
        addParam("layout_alignStart", "RelativeLayout");
        addParam("layout_alignEnd", "RelativeLayout");
        addParam("layout_alignParentStart", "RelativeLayout");
        addParam("layout_alignParentEnd", "RelativeLayout");

        // GridLayout params
        addParam("layout_row", "GridLayout");
        addParam("layout_rowSpan", "GridLayout");
        addParam("layout_rowWeight", "GridLayout");
        addParam("layout_column", "GridLayout", "TableRow");
        addParam("layout_columnSpan", "GridLayout");
        addParam("layout_columnWeight", "GridLayout");
        addParam("layout_rowGroup", "GridLayout");
        addParam("layout_columnGroup", "GridLayout");

        // TableLayout / TableRow params
        addParam("layout_span", "TableRow");

        // AbsoluteLayout params (deprecated)
        addParam("layout_x", "AbsoluteLayout");
        addParam("layout_y", "AbsoluteLayout");

        // ConstraintLayout params
        addParam("layout_constraintLeft_toLeftOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintLeft_toRightOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintRight_toLeftOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintRight_toRightOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintTop_toTopOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintTop_toBottomOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintBottom_toTopOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintBottom_toBottomOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintBaseline_toBaselineOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintStart_toEndOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintStart_toStartOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintEnd_toStartOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintEnd_toEndOf", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintDimensionRatio", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintHorizontal_bias", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintVertical_bias", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintHorizontal_chainStyle", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintVertical_chainStyle", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintWidth_default", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintHeight_default", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintWidth_min", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintWidth_max", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintHeight_min", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintHeight_max", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_editor_absoluteX", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_editor_absoluteY", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintGuide_begin", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintGuide_end", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
        addParam("layout_constraintGuide_percent", "ConstraintLayout", "android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");
    }

    private static void addParam(String param, String... parents) {
        Set<String> parentSet = PARAM_TO_PARENTS.get(param);
        if (parentSet == null) {
            parentSet = new HashSet<>();
            PARAM_TO_PARENTS.put(param, parentSet);
        }
        for (String parent : parents) {
            // Store just the simple name for matching
            parentSet.add(parent);
            // Also store the simple class name (without package)
            int dot = parent.lastIndexOf('.');
            if (dot >= 0) {
                parentSet.add(parent.substring(dot + 1));
            }
        }
    }

    // Layouts that use margin params (extend ViewGroup.MarginLayoutParams)
    private static final Set<String> MARGIN_LAYOUT_PARENTS = new HashSet<>(Arrays.asList(
            "LinearLayout",
            "RelativeLayout",
            "FrameLayout",
            "TableLayout",
            "TableRow",
            "GridLayout",
            "ScrollView",
            "HorizontalScrollView",
            "ConstraintLayout",
            "CoordinatorLayout",
            "DrawerLayout",
            "MotionLayout"
    ));

    // Layouts that do NOT use margin params
    private static final Set<String> NO_MARGIN_LAYOUT_PARENTS = new HashSet<>(Arrays.asList(
            "AbsoluteLayout"
    ));

    // Pending issues: map from element to (context, attr, location, message)
    private static class PendingIssue {
        final XmlContext context;
        final Attr attribute;
        final Location location;
        final String message;

        PendingIssue(XmlContext context, Attr attribute, Location location, String message) {
            this.context = context;
            this.attribute = attribute;
            this.location = location;
            this.message = message;
        }
    }

    private final List<PendingIssue> mPendingIssues = new ArrayList<>();

    // Map from element to its reported pending issues (so we can suppress if needed)
    // We store issues keyed by element to handle include tags etc.
    private final Map<Element, List<PendingIssue>> mElementIssues = new HashMap<>();

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        // We want to visit all elements to track parent layout types
        return ALL;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        // We want to check all layout_ attributes
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        // Only interested in layout_ params
        if (!name.startsWith("layout_")) {
            return;
        }

        // layout_width and layout_height are always valid (defined in ViewGroup.LayoutParams)
        if (VIEWGROUP_PARAMS.contains(name)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();

        // If there's no parent element (root element), layout params generally have no effect
        // unless it's a merge tag child
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Root element - layout params have no effect unless it's included somewhere
            // We can't know the parent context here, so we skip root elements
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();

        // Get simple name of parent tag
        String parentSimpleName = getSimpleName(parentTag);

        // Check if this is a merge tag - we can't determine the actual parent
        if ("merge".equals(parentTag) || "merge".equals(parentSimpleName)) {
            return;
        }

        // Check if this is an include tag - skip
        if ("include".equals(element.getTagName())) {
            return;
        }

        // Check if parent is an unknown/custom view - we can't validate
        // If parent tag contains a dot, it's a custom view - we might still check known ones
        boolean isKnownParent = isKnownLayoutParent(parentTag, parentSimpleName);

        if (!isKnownParent) {
            // Unknown parent - could support any layout params, skip
            return;
        }

        // Check margin params
        if (MARGIN_PARAMS.contains(name)) {
            if (!supportsMarginParams(parentTag, parentSimpleName)) {
                reportIssue(context, attribute, element, name, parentTag);
            }
            return;
        }

        // Check specific layout params
        if (PARAM_TO_PARENTS.containsKey(name)) {
            Set<String> validParents = PARAM_TO_PARENTS.get(name);
            if (!isValidParent(parentTag, parentSimpleName, validParents)) {
                reportIssue(context, attribute, element, name, parentTag);
            }
            return;
        }

        // For other layout_ params not in our map, we don't flag them
        // as we might not have complete knowledge
    }

    private void reportIssue(
            @NonNull XmlContext context,
            @NonNull Attr attribute,
            @NonNull Element element,
            @NonNull String paramName,
            @NonNull String parentTag) {
        String message = String.format(
                "Invalid layout param '%1$s' (defined for parent layout '%2$s' but used in '%3$s')",
                paramName,
                getExpectedParents(paramName),
                parentTag);

        Location location = context.getLocation(attribute);
        PendingIssue issue = new PendingIssue(context, attribute, location, message);
        mPendingIssues.add(issue);

        List<PendingIssue> elementIssues = mElementIssues.get(element);
        if (elementIssues == null) {
            elementIssues = new ArrayList<>();
            mElementIssues.put(element, elementIssues);
        }
        elementIssues.add(issue);
    }

    private String getExpectedParents(String paramName) {
        if (MARGIN_PARAMS.contains(paramName)) {
            return "ViewGroup with MarginLayoutParams";
        }
        Set<String> parents = PARAM_TO_PARENTS.get(paramName);
        if (parents == null || parents.isEmpty()) {
            return "unknown";
        }
        // Return a representative parent
        // Filter to simple names only for display
        List<String> simpleNames = new ArrayList<>();
        for (String p : parents) {
            if (!p.contains(".")) {
                simpleNames.add(p);
            }
        }
        if (!simpleNames.isEmpty()) {
            Collections.sort(simpleNames);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < simpleNames.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(simpleNames.get(i));
            }
            return sb.toString();
        }
        return parents.iterator().next();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We use this to track elements but primary logic is in visitAttribute
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        // Report all pending issues
        for (PendingIssue issue : mPendingIssues) {
            issue.context.report(
                    ISSUE,
                    issue.attribute,
                    issue.location,
                    issue.message);
        }
        mPendingIssues.clear();
        mElementIssues.clear();
    }

    private boolean isKnownLayoutParent(String fullTag, String simpleName) {
        // Known layout containers
        Set<String> knownLayouts = new HashSet<>(Arrays.asList(
                "LinearLayout",
                "RelativeLayout",
                "FrameLayout",
                "TableLayout",
                "TableRow",
                "GridLayout",
                "ScrollView",
                "HorizontalScrollView",
                "AbsoluteLayout",
                "ConstraintLayout",
                "CoordinatorLayout",
                "DrawerLayout",
                "MotionLayout",
                "ViewGroup",
                "android.widget.LinearLayout",
                "android.widget.RelativeLayout",
                "android.widget.FrameLayout",
                "android.widget.TableLayout",
                "android.widget.TableRow",
                "android.widget.GridLayout",
                "android.widget.ScrollView",
                "android.widget.HorizontalScrollView",
                "android.widget.AbsoluteLayout",
                "androidx.constraintlayout.widget.ConstraintLayout",
                "android.support.constraint.ConstraintLayout",
                "androidx.coordinatorlayout.widget.CoordinatorLayout",
                "android.support.v4.widget.DrawerLayout",
                "androidx.drawerlayout.widget.DrawerLayout",
                "androidx.constraintlayout.motion.widget.MotionLayout"
        ));

        return knownLayouts.contains(fullTag) || knownLayouts.contains(simpleName);
    }

    private boolean supportsMarginParams(String fullTag, String simpleName) {
        if (NO_MARGIN_LAYOUT_PARENTS.contains(fullTag)
                || NO_MARGIN_LAYOUT_PARENTS.contains(simpleName)) {
            return false;
        }
        return MARGIN_LAYOUT_PARENTS.contains(fullTag)
                || MARGIN_LAYOUT_PARENTS.contains(simpleName);
    }

    private boolean isValidParent(String fullTag, String simpleName, Set<String> validParents) {
        return validParents.contains(fullTag) || validParents.contains(simpleName);
    }

    private static String getSimpleName(String tag) {
        int dot = tag.lastIndexOf('.');
        if (dot >= 0) {
            return tag.substring(dot + 1);
        }
        return tag;
    }
}