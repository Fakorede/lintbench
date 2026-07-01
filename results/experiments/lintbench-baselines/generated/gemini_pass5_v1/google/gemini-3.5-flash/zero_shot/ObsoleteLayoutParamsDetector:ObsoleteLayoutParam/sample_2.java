package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
        "ObsoleteLayoutParam",
        "Obsolete layout params",
        "The given layout_param is not defined for the given layout, meaning it has no "
            + "effect. This usually happens when you change the parent layout or move "
            + "view code around without updating the layout params. This will cause "
            + "useless attribute processing at runtime, and is misleading for others "
            + "reading the layout so the parameter should be removed.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            ObsoleteLayoutParamsDetector.class,
            Scope.LAYOUT_RESOURCE_FILES
        )
    );

    private static final String[] RELATIVE_LAYOUT_DISALLOWED = {
        "LinearLayout", "android.widget.LinearLayout",
        "FrameLayout", "android.widget.FrameLayout",
        "GridLayout", "android.widget.GridLayout", "androidx.gridlayout.widget.GridLayout",
        "ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout",
        "CoordinatorLayout", "androidx.coordinatorlayout.widget.CoordinatorLayout",
        "TableLayout", "android.widget.TableLayout",
        "TableRow", "android.widget.TableRow",
        "ScrollView", "android.widget.ScrollView",
        "NestedScrollView", "androidx.core.widget.NestedScrollView"
    };

    private static final String[] GRAVITY_DISALLOWED = {
        "RelativeLayout", "android.widget.RelativeLayout",
        "ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout",
        "AbsoluteLayout", "android.widget.AbsoluteLayout"
    };

    private static final String[] WEIGHT_DISALLOWED = {
        "RelativeLayout", "android.widget.RelativeLayout",
        "FrameLayout", "android.widget.FrameLayout",
        "GridLayout", "android.widget.GridLayout", "androidx.gridlayout.widget.GridLayout",
        "ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout",
        "CoordinatorLayout", "androidx.coordinatorlayout.widget.CoordinatorLayout",
        "AbsoluteLayout", "android.widget.AbsoluteLayout",
        "ScrollView", "android.widget.ScrollView",
        "NestedScrollView", "androidx.core.widget.NestedScrollView"
    };

    private static final String[] GRID_LAYOUT_DISALLOWED = {
        "LinearLayout", "android.widget.LinearLayout",
        "RelativeLayout", "android.widget.RelativeLayout",
        "FrameLayout", "android.widget.FrameLayout",
        "ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout",
        "CoordinatorLayout", "androidx.coordinatorlayout.widget.CoordinatorLayout",
        "TableLayout", "android.widget.TableLayout",
        "TableRow", "android.widget.TableRow",
        "ScrollView", "android.widget.ScrollView",
        "NestedScrollView", "androidx.core.widget.NestedScrollView"
    };

    private static final String[] ABSOLUTE_LAYOUT_DISALLOWED = {
        "LinearLayout", "android.widget.LinearLayout",
        "RelativeLayout", "android.widget.RelativeLayout",
        "FrameLayout", "android.widget.FrameLayout",
        "GridLayout", "android.widget.GridLayout", "androidx.gridlayout.widget.GridLayout",
        "ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout",
        "CoordinatorLayout", "androidx.coordinatorlayout.widget.CoordinatorLayout",
        "TableLayout", "android.widget.TableLayout",
        "TableRow", "android.widget.TableRow",
        "ScrollView", "android.widget.ScrollView",
        "NestedScrollView", "androidx.core.widget.NestedScrollView"
    };

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
            "layout_alignParentTop", "layout_alignParentBottom",
            "layout_alignParentLeft", "layout_alignParentRight",
            "layout_alignParentStart", "layout_alignParentEnd",
            "layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical",
            "layout_alignTop", "layout_alignBottom", "layout_alignLeft", "layout_alignRight",
            "layout_alignStart", "layout_alignEnd", "layout_alignBaseline",
            "layout_toLeftOf", "layout_toRightOf", "layout_toStartOf", "layout_toEndOf",
            "layout_above", "layout_below",
            "layout_gravity", "layout_weight",
            "layout_row", "layout_rowSpan", "layout_rowWeight",
            "layout_column", "layout_columnSpan", "layout_columnWeight",
            "layout_x", "layout_y"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        if ("merge".equals(parentTag)) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }

        boolean isObsolete = false;

        if (isRelativeLayoutAttribute(name)) {
            if (isParentOfAny(parentTag, RELATIVE_LAYOUT_DISALLOWED)) {
                isObsolete = true;
            }
        } else if ("layout_gravity".equals(name)) {
            if (isParentOfAny(parentTag, GRAVITY_DISALLOWED)) {
                isObsolete = true;
            }
        } else if ("layout_weight".equals(name)) {
            if (isParentOfAny(parentTag, WEIGHT_DISALLOWED)) {
                isObsolete = true;
            }
        } else if (isGridLayoutAttribute(name)) {
            if (isParentOfAny(parentTag, GRID_LAYOUT_DISALLOWED)) {
                isObsolete = true;
            }
        } else if ("layout_x".equals(name) || "layout_y".equals(name)) {
            if (isParentOfAny(parentTag, ABSOLUTE_LAYOUT_DISALLOWED)) {
                isObsolete = true;
            }
        }

        if (isObsolete) {
            String msg = String.format(
                "The `%s` attribute is not defined for `%s`, meaning it has no effect",
                attribute.getName(),
                parentTag
            );
            LintFix fix = fix()
                .unset(attribute.getNamespaceURI(), name)
                .name("Remove attribute")
                .autoFix()
                .build();
            context.report(ISSUE, attribute, context.getLocation(attribute), msg, fix);
        }
    }

    private boolean isRelativeLayoutAttribute(String name) {
        switch (name) {
            case "layout_alignParentTop":
            case "layout_alignParentBottom":
            case "layout_alignParentLeft":
            case "layout_alignParentRight":
            case "layout_alignParentStart":
            case "layout_alignParentEnd":
            case "layout_centerInParent":
            case "layout_centerHorizontal":
            case "layout_centerVertical":
            case "layout_alignTop":
            case "layout_alignBottom":
            case "layout_alignLeft":
            case "layout_alignRight":
            case "layout_alignStart":
            case "layout_alignEnd":
            case "layout_alignBaseline":
            case "layout_toLeftOf":
            case "layout_toRightOf":
            case "layout_toStartOf":
            case "layout_toEndOf":
            case "layout_above":
            case "layout_below":
                return true;
            default:
                return false;
        }
    }

    private boolean isGridLayoutAttribute(String name) {
        switch (name) {
            case "layout_row":
            case "layout_rowSpan":
            case "layout_rowWeight":
            case "layout_column":
            case "layout_columnSpan":
            case "layout_columnWeight":
                return true;
            default:
                return false;
        }
    }

    private boolean isParentOfAny(String parentTag, String[] disallowedParents) {
        String simpleName = parentTag;
        int lastDot = parentTag.lastIndexOf('.');
        if (lastDot != -1) {
            simpleName = parentTag.substring(lastDot + 1);
        }
        for (String tag : disallowedParents) {
            if (tag.equals(parentTag) || tag.equals(simpleName)) {
                return true;
            }
        }
        return false;
    }
}