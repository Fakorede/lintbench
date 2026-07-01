package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
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
                    "The given layout_param is not defined for the given layout, meaning it has no effect. "
                            + "This usually happens when you change the parent layout or move view code around "
                            + "without updating the layout params. This will cause useless attribute processing "
                            + "at runtime, and is misleading for others reading the layout so the parameter "
                            + "should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String TOOLS_URI = "http://schemas.android.com/tools";

    private static final Set<String> RELATIVE_LAYOUTS = unmodifiableSet("RelativeLayout");
    private static final Set<String> LINEAR_LAYOUTS =
            unmodifiableSet(
                    "LinearLayout",
                    "SlidingPaneLayout",
                    "RadioGroup",
                    "TableRow",
                    "TabWidget",
                    "AppBarLayout");
    private static final Set<String> TABLE_ROW_LAYOUTS = unmodifiableSet("TableRow");
    private static final Set<String> GRID_LAYOUTS =
            unmodifiableSet("GridLayout", "androidx.gridlayout.widget.GridLayout");
    private static final Set<String> COORDINATOR_LAYOUTS =
            unmodifiableSet(
                    "CoordinatorLayout",
                    "androidx.coordinatorlayout.widget.CoordinatorLayout");
    private static final Set<String> CONSTRAINT_LAYOUTS =
            unmodifiableSet(
                    "ConstraintLayout",
                    "androidx.constraintlayout.widget.ConstraintLayout");

    private static final Map<String, Set<String>> LAYOUT_PARAM_TO_LAYOUTS = new HashMap<>();

    private static final Set<String> KNOWN_LAYOUTS;

    static {
        for (String attr : new String[]{
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
                "layout_alignBaseline",
                "layout_above",
                "layout_below",
                "layout_toEndOf",
                "layout_toLeftOf",
                "layout_toRightOf",
                "layout_toStartOf",
                "layout_centerHorizontal",
                "layout_centerInParent",
                "layout_centerVertical"
        }) {
            LAYOUT_PARAM_TO_LAYOUTS.put(attr, RELATIVE_LAYOUTS);
        }

        LAYOUT_PARAM_TO_LAYOUTS.put("layout_weight", LINEAR_LAYOUTS);

        Set<String> tableAndGrid = new HashSet<>(TABLE_ROW_LAYOUTS);
        tableAndGrid.addAll(GRID_LAYOUTS);
        LAYOUT_PARAM_TO_LAYOUTS.put(
                "layout_column", Collections.unmodifiableSet(tableAndGrid));
        LAYOUT_PARAM_TO_LAYOUTS.put("layout_span", TABLE_ROW_LAYOUTS);

        for (String attr : new String[]{
                "layout_columnSpan",
                "layout_columnWeight",
                "layout_row",
                "layout_rowSpan",
                "layout_rowWeight"
        }) {
            LAYOUT_PARAM_TO_LAYOUTS.put(attr, GRID_LAYOUTS);
        }

        for (String attr : new String[]{
                "layout_behavior",
                "layout_anchor",
                "layout_anchorGravity",
                "layout_dodgeInsetEdges",
                "layout_insetEdge",
                "layout_keyline"
        }) {
            LAYOUT_PARAM_TO_LAYOUTS.put(attr, COORDINATOR_LAYOUTS);
        }

        Set<String> known = new HashSet<>();
        known.addAll(RELATIVE_LAYOUTS);
        known.addAll(LINEAR_LAYOUTS);
        known.addAll(TABLE_ROW_LAYOUTS);
        known.addAll(GRID_LAYOUTS);
        known.addAll(COORDINATOR_LAYOUTS);
        known.addAll(CONSTRAINT_LAYOUTS);
        known.add("AbsoluteLayout");
        known.add("AdapterView");
        known.add("DrawerLayout");
        known.add("FrameLayout");
        known.add("GridView");
        known.add("HorizontalScrollView");
        known.add("ListView");
        known.add("RecyclerView");
        known.add("ScrollView");
        known.add("SwipeRefreshLayout");
        known.add("TableLayout");
        known.add("Toolbar");
        known.add("ViewAnimator");
        known.add("ViewFlipper");
        known.add("ViewPager");
        known.add("ViewSwitcher");
        KNOWN_LAYOUTS = Collections.unmodifiableSet(known);
    }

    private static Set<String> unmodifiableSet(String... items) {
        Set<String> set = new HashSet<>(items.length);
        Collections.addAll(set, items);
        return Collections.unmodifiableSet(set);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if (TOOLS_URI.equals(namespace)) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        Set<String> layouts = LAYOUT_PARAM_TO_LAYOUTS.get(name);
        if (layouts == null) {
            if (name.startsWith("layout_constraint")
                    || name.startsWith("layout_goneMargin")
                    || "layout_editor_absoluteX".equals(name)
                    || "layout_editor_absoluteY".equals(name)
                    || "layout_marginBaseline".equals(name)) {
                layouts = CONSTRAINT_LAYOUTS;
            } else {
                return;
            }
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        Node parentNode = owner.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        String parentSimpleName = getSimpleName(parentTag);

        if (!KNOWN_LAYOUTS.contains(parentTag) && !KNOWN_LAYOUTS.contains(parentSimpleName)) {
            return;
        }

        if (!layouts.contains(parentTag) && !layouts.contains(parentSimpleName)) {
            String message =
                    String.format(
                            "Invalid layout param in a `%1$s`: `%2$s`",
                            parentSimpleName, name);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Obsolete layout params are detected on attributes; no element-level work needed.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No global state to clean up.
    }

    private static String getSimpleName(String className) {
        int index = className.lastIndexOf('.');
        return index == -1 ? className : className.substring(index + 1);
    }
}