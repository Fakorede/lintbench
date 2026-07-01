package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class ObsoleteLayoutParamsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getNodeName();
        }
        int colon = name.indexOf(':');
        if (colon != -1) {
            name = name.substring(colon + 1);
        }

        if (!name.startsWith("layout_")) {
            return;
        }

        if (name.equals("layout_width") || name.equals("layout_height")) {
            return;
        }
        if (name.startsWith("layout_margin")) {
            return;
        }
        if (name.equals("layoutAnimation") || name.equals("layoutResource") || name.equals("layoutDescription")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String tag = element.getTagName();
        if (tag.equals("merge") || tag.equals("include")) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        if (parentTag.equals("merge")) {
            return;
        }

        String simpleParent = getSimpleParentTag(parentTag);
        if (simpleParent == null) {
            return;
        }

        if (isObsoleteForParent(simpleParent, name)) {
            context.report(ISSUE, context.getLocation(attribute),
                    "Invalid layout param in a `" + simpleParent + "`: `" + name + "`");
        }
    }

    private static String getSimpleParentTag(String tag) {
        if (tag.indexOf('.') == -1) {
            return tag;
        }
        if (tag.endsWith(".ConstraintLayout") || tag.endsWith(".MotionLayout")) return "ConstraintLayout";
        if (tag.endsWith(".CoordinatorLayout")) return "CoordinatorLayout";
        if (tag.endsWith(".AppBarLayout")) return "AppBarLayout";
        if (tag.endsWith(".CollapsingToolbarLayout")) return "CollapsingToolbarLayout";
        if (tag.endsWith(".DrawerLayout")) return "DrawerLayout";
        if (tag.endsWith(".GridLayout")) return "GridLayout";
        if (tag.endsWith(".ViewPager2")) return "ViewPager2";
        if (tag.endsWith(".RecyclerView")) return "RecyclerView";
        if (tag.endsWith(".SwipeRefreshLayout")) return "SwipeRefreshLayout";
        if (tag.endsWith(".FragmentContainerView")) return "FragmentContainerView";
        if (tag.endsWith(".NestedScrollView")) return "NestedScrollView";
        if (tag.endsWith(".CardView")) return "CardView";
        if (tag.endsWith(".LinearLayout")) return "LinearLayout";
        if (tag.endsWith(".RelativeLayout")) return "RelativeLayout";
        if (tag.endsWith(".FrameLayout")) return "FrameLayout";
        if (tag.endsWith(".TableLayout")) return "TableLayout";
        if (tag.endsWith(".TableRow")) return "TableRow";
        if (tag.endsWith(".ScrollView")) return "ScrollView";
        if (tag.endsWith(".HorizontalScrollView")) return "HorizontalScrollView";
        if (tag.endsWith(".AbsoluteLayout")) return "AbsoluteLayout";
        return null;
    }

    private static boolean isObsoleteForParent(String parent, String attr) {
        switch (parent) {
            case "LinearLayout":
            case "TableLayout":
                return !attr.equals("layout_weight") && !attr.equals("layout_gravity");
            case "TableRow":
                return !attr.equals("layout_column") && !attr.equals("layout_span") && !attr.equals("layout_gravity") && !attr.equals("layout_weight");
            case "FrameLayout":
            case "DrawerLayout":
                return !attr.equals("layout_gravity");
            case "GridLayout":
                return !isGridParam(attr);
            case "RelativeLayout":
                return !isRelativeParam(attr);
            case "ConstraintLayout":
                return !isConstraintParam(attr);
            case "CoordinatorLayout":
            case "AppBarLayout":
            case "CollapsingToolbarLayout":
                return !isCoordinatorParam(attr);
            case "AbsoluteLayout":
                return !attr.equals("layout_x") && !attr.equals("layout_y");
            case "ViewPager":
            case "ViewPager2":
            case "RecyclerView":
            case "ScrollView":
            case "HorizontalScrollView":
            case "NestedScrollView":
            case "SwipeRefreshLayout":
            case "FragmentContainerView":
            case "CardView":
            case "AdapterView":
            case "ListView":
            case "GridView":
                return true;
            default:
                return false;
        }
    }

    private static boolean isRelativeParam(String attr) {
        return attr.startsWith("layout_align") ||
               attr.startsWith("layout_center") ||
               attr.startsWith("layout_to") ||
               attr.equals("layout_above") ||
               attr.equals("layout_below") ||
               attr.equals("layout_alignWithParentIfMissing");
    }

    private static boolean isConstraintParam(String attr) {
        return attr.startsWith("layout_constraint") ||
               attr.startsWith("layout_goneMargin") ||
               attr.startsWith("layout_editor_") ||
               attr.equals("layout_wrapBehaviorInParent") ||
               attr.startsWith("layout_constrained");
    }

    private static boolean isGridParam(String attr) {
        return attr.startsWith("layout_row") ||
               attr.startsWith("layout_column") ||
               attr.equals("layout_gravity");
    }

    private static boolean isCoordinatorParam(String attr) {
        return attr.equals("layout_gravity") ||
               attr.equals("layout_anchor") ||
               attr.equals("layout_anchorGravity") ||
               attr.equals("layout_behavior") ||
               attr.equals("layout_insetEdge") ||
               attr.equals("layout_dodgeInsetEdges") ||
               attr.equals("layout_keyline") ||
               attr.startsWith("layout_scroll") ||
               attr.startsWith("layout_collapse");
    }
}