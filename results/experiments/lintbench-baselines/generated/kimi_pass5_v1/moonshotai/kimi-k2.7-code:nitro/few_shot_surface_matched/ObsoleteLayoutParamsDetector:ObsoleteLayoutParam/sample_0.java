package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    4,
                    Severity.WARNING,
                    new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Set<String> ALWAYS_VALID = new HashSet<>();
    private static final Map<String, Set<String>> VALID_PARENTS = new HashMap<>();

    static {
        ALWAYS_VALID.add("layout_width");
        ALWAYS_VALID.add("layout_height");
        ALWAYS_VALID.add("layout_margin");
        ALWAYS_VALID.add("layout_marginLeft");
        ALWAYS_VALID.add("layout_marginRight");
        ALWAYS_VALID.add("layout_marginTop");
        ALWAYS_VALID.add("layout_marginBottom");
        ALWAYS_VALID.add("layout_marginStart");
        ALWAYS_VALID.add("layout_marginEnd");
        ALWAYS_VALID.add("layout_marginHorizontal");
        ALWAYS_VALID.add("layout_marginVertical");

        add("layout_gravity",
                "FrameLayout", "LinearLayout", "GridLayout", "TableRow",
                "DrawerLayout", "CoordinatorLayout", "RadioGroup", "PercentFrameLayout");

        add("layout_weight",
                "LinearLayout", "TableLayout", "TableRow", "RadioGroup");

        add("layout_above", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignBaseline", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignBottom", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignEnd", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignLeft", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignRight", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignStart", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignTop", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignParentBottom", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignParentEnd", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignParentLeft", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignParentRight", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignParentStart", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignParentTop", "RelativeLayout", "PercentRelativeLayout");
        add("layout_alignWithParentIfMissing", "RelativeLayout", "PercentRelativeLayout");
        add("layout_below", "RelativeLayout", "PercentRelativeLayout");
        add("layout_centerHorizontal", "RelativeLayout", "PercentRelativeLayout");
        add("layout_centerInParent", "RelativeLayout", "PercentRelativeLayout");
        add("layout_centerVertical", "RelativeLayout", "PercentRelativeLayout");
        add("layout_toEndOf", "RelativeLayout", "PercentRelativeLayout");
        add("layout_toLeftOf", "RelativeLayout", "PercentRelativeLayout");
        add("layout_toRightOf", "RelativeLayout", "PercentRelativeLayout");
        add("layout_toStartOf", "RelativeLayout", "PercentRelativeLayout");

        add("layout_column", "GridLayout", "TableRow");
        add("layout_columnSpan", "GridLayout");
        add("layout_row", "GridLayout");
        add("layout_rowSpan", "GridLayout");

        add("layout_span", "TableRow");

        add("layout_x", "AbsoluteLayout");
        add("layout_y", "AbsoluteLayout");

        add("layout_anchor", "CoordinatorLayout");
        add("layout_anchorGravity", "CoordinatorLayout");
        add("layout_behavior", "CoordinatorLayout");
        add("layout_dodgeInsetEdges", "CoordinatorLayout");
        add("layout_insetEdge", "CoordinatorLayout");
        add("layout_keyline", "CoordinatorLayout");

        add("layout_scrollFlags", "AppBarLayout");
        add("layout_scrollInterpolator", "AppBarLayout");
    }

    private static void add(String attribute, String... parents) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, parents);
        VALID_PARENTS.put(attribute, set);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Nothing to do here; the check is attribute-driven.
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        if (ALWAYS_VALID.contains(name)) {
            return;
        }

        Node parentNode = attribute.getOwnerElement().getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        String parentTag = ((Element) parentNode).getTagName();
        int dot = parentTag.lastIndexOf('.');
        if (dot != -1) {
            parentTag = parentTag.substring(dot + 1);
        }

        if (name.startsWith("layout_constraint")
                || name.startsWith("layout_goneMargin")
                || name.startsWith("layout_editor_absolute")) {
            if (!"ConstraintLayout".equals(parentTag)) {
                report(context, attribute, name, parentTag);
            }
            return;
        }

        Set<String> valid = VALID_PARENTS.get(name);
        if (valid == null) {
            return;
        }

        if (!valid.contains(parentTag)) {
            report(context, attribute, name, parentTag);
        }
    }

    private void report(XmlContext context, Attr attribute, String name, String parentTag) {
        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                String.format(
                        "The layout attribute %1$s is not defined for the parent layout %2$s and will have no effect",
                        name, parentTag));
    }

    @Override
    public void afterCheckRootProject(Context context) {
        // No post-project cleanup required.
    }
}