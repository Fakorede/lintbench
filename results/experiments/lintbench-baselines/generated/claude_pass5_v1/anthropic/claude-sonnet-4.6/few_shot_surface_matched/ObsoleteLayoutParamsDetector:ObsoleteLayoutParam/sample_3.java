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
                    "The given `layout_param` is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    // Namespace for Android attributes
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    // Layout param attributes defined by specific parent layouts
    // These are attributes that only make sense under certain parent layouts

    // Attributes only valid under LinearLayout
    private static final Set<String> LINEAR_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_weight",
            "layout_gravity"
    ));

    // Attributes only valid under RelativeLayout
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
            "layout_centerInParent"
    ));

    // Attributes only valid under GridLayout
    private static final Set<String> GRID_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_row",
            "layout_rowSpan",
            "layout_rowWeight",
            "layout_column",
            "layout_columnSpan",
            "layout_columnWeight"
    ));

    // Attributes only valid under TableRow
    private static final Set<String> TABLE_ROW_PARAMS = new HashSet<>(Arrays.asList(
            "layout_column",
            "layout_span"
    ));

    // Attributes only valid under AbsoluteLayout
    private static final Set<String> ABSOLUTE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_x",
            "layout_y"
    ));

    // Map from layout_param local name -> set of parent layouts that support it
    private static final Map<String, Set<String>> PARAM_TO_LAYOUTS = new HashMap<>();

    static {
        for (String param : LINEAR_LAYOUT_PARAMS) {
            addMapping(param, "LinearLayout");
        }
        // layout_gravity is also valid in FrameLayout
        addMapping("layout_gravity", "FrameLayout");
        addMapping("layout_gravity", "ScrollView");
        addMapping("layout_gravity", "HorizontalScrollView");

        for (String param : RELATIVE_LAYOUT_PARAMS) {
            addMapping(param, "RelativeLayout");
        }

        for (String param : GRID_LAYOUT_PARAMS) {
            addMapping(param, "GridLayout");
        }

        // layout_column and layout_span are TableRow params
        addMapping("layout_column", "TableRow");
        addMapping("layout_span", "TableRow");

        for (String param : ABSOLUTE_LAYOUT_PARAMS) {
            addMapping(param, "AbsoluteLayout");
        }
    }

    private static void addMapping(String param, String layout) {
        Set<String> layouts = PARAM_TO_LAYOUTS.get(param);
        if (layouts == null) {
            layouts = new HashSet<>();
            PARAM_TO_LAYOUTS.put(param, layouts);
        }
        layouts.add(layout);
    }

    // All layout_* attributes we want to check (those that are parent-specific)
    private static final Set<String> ALL_SPECIFIC_PARAMS = PARAM_TO_LAYOUTS.keySet();

    // Pending issues: we collect them during visitAttribute and resolve after the file is done
    // because we need to know the parent element's tag
    // Map from attribute node -> XmlContext (for deferred reporting)
    // We store issues as a list of pending reports
    private static class PendingIssue {
        final XmlContext context;
        final Attr attribute;
        final String parentTag;
        final String attrName;

        PendingIssue(XmlContext context, Attr attribute, String parentTag, String attrName) {
            this.context = context;
            this.attribute = attribute;
            this.parentTag = parentTag;
            this.attrName = attrName;
        }
    }

    private final List<PendingIssue> mPendingIssues = new ArrayList<>();

    // Track include elements and their layouts for deferred resolution
    // Map from element id (or object identity) to included layout id
    // We don't resolve includes across files in this simplified implementation

    // Tag names of elements we want to visit
    private static final List<String> APPLICABLE_ELEMENTS = Arrays.asList(
            "LinearLayout",
            "RelativeLayout",
            "FrameLayout",
            "GridLayout",
            "TableLayout",
            "TableRow",
            "AbsoluteLayout",
            "ScrollView",
            "HorizontalScrollView",
            "RadioGroup",
            "merge",
            "include",
            "ViewGroup"
    );

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return APPLICABLE_ELEMENTS;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        // Return all parent-specific layout params
        return new ArrayList<>(ALL_SPECIFIC_PARAMS);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We use visitElement to track the structure but main logic is in visitAttribute
        // This can be used for future enhancements
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            localName = attribute.getName();
            if (localName != null && localName.contains(":")) {
                localName = localName.substring(localName.indexOf(':') + 1);
            }
        }

        if (localName == null || !localName.startsWith("layout_")) {
            return;
        }

        // Check if this is a parent-specific param
        if (!ALL_SPECIFIC_PARAMS.contains(localName)) {
            return;
        }

        // Get the element that owns this attribute
        Element element = attribute.getOwnerElement();

        // Get the parent element
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Root element or no parent - these layout params have no effect
            // but we skip root elements as they might be valid in include scenarios
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        // Strip namespace prefix from parent tag if present
        if (parentTag.contains(":")) {
            parentTag = parentTag.substring(parentTag.indexOf(':') + 1);
        }

        // Get the simple name of the parent (strip package prefix)
        String parentSimpleName = parentTag;
        if (parentSimpleName.contains(".")) {
            parentSimpleName = parentSimpleName.substring(parentSimpleName.lastIndexOf('.') + 1);
        }

        // Check if this layout param is valid for the parent
        Set<String> validLayouts = PARAM_TO_LAYOUTS.get(localName);
        if (validLayouts == null) {
            return;
        }

        // Special cases: merge and include can have any layout params (resolved at runtime)
        if (parentSimpleName.equals("merge") || parentSimpleName.equals("include")) {
            return;
        }

        // If the parent is a known layout that doesn't support this param, report it
        boolean parentIsKnown = isKnownLayout(parentSimpleName);
        if (!parentIsKnown) {
            // Unknown/custom layout - skip to avoid false positives
            return;
        }

        if (!validLayouts.contains(parentSimpleName)) {
            // This layout param is not valid for this parent
            mPendingIssues.add(new PendingIssue(context, attribute, parentTag, localName));
        }
    }

    private boolean isKnownLayout(String tagName) {
        switch (tagName) {
            case "LinearLayout":
            case "RelativeLayout":
            case "FrameLayout":
            case "GridLayout":
            case "TableLayout":
            case "TableRow":
            case "AbsoluteLayout":
            case "ScrollView":
            case "HorizontalScrollView":
            case "RadioGroup":
            case "ConstraintLayout":
            case "CoordinatorLayout":
            case "AppBarLayout":
            case "CollapsingToolbarLayout":
            case "DrawerLayout":
            case "ViewPager":
            case "RecyclerView":
            case "ListView":
            case "GridView":
                return true;
            default:
                return false;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        for (PendingIssue pending : mPendingIssues) {
            String message = String.format(
                    "Invalid layout param `%1$s` (not a param of the parent layout `%2$s`)",
                    pending.attrName, pending.parentTag);
            Location location = pending.context.getLocation(pending.attribute);
            pending.context.report(ISSUE, pending.attribute, location, message);
        }
        mPendingIssues.clear();
    }
}