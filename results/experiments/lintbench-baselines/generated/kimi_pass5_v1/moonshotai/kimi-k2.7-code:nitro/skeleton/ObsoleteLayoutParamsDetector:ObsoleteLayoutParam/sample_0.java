package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
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

    private static final Set<String> GRAVITY_LAYOUTS =
            new HashSet<>(
                    Arrays.asList(
                            "FrameLayout",
                            "LinearLayout",
                            "LinearLayoutCompat",
                            "GridLayout",
                            "TableLayout",
                            "TableRow",
                            "DrawerLayout",
                            "SlidingPaneLayout",
                            "ViewPager",
                            "PagerTitleStrip",
                            "PagerTabStrip",
                            "Toolbar",
                            "ActionMenuView",
                            "RadioGroup",
                            "NestedScrollView",
                            "HorizontalScrollView",
                            "ScrollView",
                            "CardView",
                            "MaterialCardView",
                            "TabLayout",
                            "AppBarLayout",
                            "CollapsingToolbarLayout",
                            "PercentFrameLayout",
                            "SearchView",
                            "FragmentContainerView"));

    private static final Set<String> WEIGHT_LAYOUTS =
            new HashSet<>(
                    Arrays.asList(
                            "LinearLayout",
                            "LinearLayoutCompat",
                            "TableLayout",
                            "TableRow",
                            "RadioGroup",
                            "SlidingPaneLayout",
                            "AppBarLayout",
                            "SearchView"));

    private final Map<Element, String> mLayoutClasses = new IdentityHashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mLayoutClasses.put(element, resolveLayoutClass(element));
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        if ("layout_width".equals(name) || "layout_height".equals(name)) {
            return;
        }

        if (name.startsWith("layout_margin")) {
            return;
        }

        Element child = attribute.getOwnerElement();
        if (child == null) {
            return;
        }

        Node parentNode = child.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentClass = mLayoutClasses.get(parent);
        if (parentClass == null) {
            parentClass = resolveLayoutClass(parent);
            mLayoutClasses.put(parent, parentClass);
        }

        int validity = checkLayoutParam(name, parentClass);
        if (validity == -1) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Obsolete layout param: " + name + " is not valid for a " + parentClass);
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        mLayoutClasses.clear();
    }

    private static String resolveLayoutClass(Element element) {
        String tag = element.getTagName();
        if ("view".equals(tag)) {
            String cls = element.getAttribute("class");
            if (cls != null && !cls.isEmpty()) {
                return getSimpleClassName(cls);
            }
        }
        return getSimpleClassName(tag);
    }

    private static String getSimpleClassName(String name) {
        if (name == null) {
            return null;
        }
        int index = name.lastIndexOf('.');
        return index == -1 ? name : name.substring(index + 1);
    }

    private static int checkLayoutParam(String attr, String parentClass) {
        if (parentClass == null) {
            return 0;
        }

        if ("layout_gravity".equals(attr)) {
            return GRAVITY_LAYOUTS.contains(parentClass) ? 1 : -1;
        }

        if ("layout_weight".equals(attr)) {
            return WEIGHT_LAYOUTS.contains(parentClass) ? 1 : -1;
        }

        if ("layout_column".equals(attr)) {
            return parentClass.equals("GridLayout") || parentClass.equals("TableRow") ? 1 : -1;
        }

        if ("layout_span".equals(attr)) {
            return parentClass.equals("TableRow") ? 1 : -1;
        }

        if ("layout_columnSpan".equals(attr)
                || "layout_rowSpan".equals(attr)
                || "layout_row".equals(attr)) {
            return parentClass.equals("GridLayout") ? 1 : -1;
        }

        if (isRelativeAttribute(attr)) {
            return parentClass.contains("RelativeLayout") ? 1 : -1;
        }

        if (isConstraintAttribute(attr)) {
            return parentClass.contains("ConstraintLayout") || parentClass.contains("MotionLayout")
                    ? 1
                    : -1;
        }

        if (isCoordinatorAttribute(attr)) {
            return parentClass.contains("CoordinatorLayout") ? 1 : -1;
        }

        if ("layout_scrollFlags".equals(attr) || "layout_scrollInterpolator".equals(attr)) {
            return parentClass.contains("AppBarLayout") ? 1 : -1;
        }

        if ("layout_collapseMode".equals(attr)
                || "layout_collapseParallaxMultiplier".equals(attr)) {
            return parentClass.contains("CollapsingToolbarLayout") ? 1 : -1;
        }

        return 0;
    }

    private static boolean isRelativeAttribute(String attr) {
        return attr.startsWith("layout_align")
                || attr.startsWith("layout_to")
                || "layout_above".equals(attr)
                || "layout_below".equals(attr)
                || "layout_centerHorizontal".equals(attr)
                || "layout_centerInParent".equals(attr)
                || "layout_centerVertical".equals(attr);
    }

    private static boolean isConstraintAttribute(String attr) {
        return attr.startsWith("layout_constraint")
                || attr.startsWith("layout_goneMargin")
                || attr.startsWith("layout_editor")
                || "layout_constrainedWidth".equals(attr)
                || "layout_constrainedHeight".equals(attr)
                || "layout_chainUseRtl".equals(attr);
    }

    private static boolean isCoordinatorAttribute(String attr) {
        return "layout_behavior".equals(attr)
                || "layout_anchor".equals(attr)
                || "layout_anchorGravity".equals(attr)
                || "layout_dodgeInsetEdges".equals(attr)
                || "layout_insetEdge".equals(attr)
                || "layout_keyline".equals(attr);
    }
}