package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.TOOLS_URI;

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
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ObsoleteLayoutParamsDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    private static final String ATTR_LAYOUT_PREFIX = "layout_";

    private static final Map<String, Set<String>> ATTRIBUTES_BY_TAG =
            new HashMap<String, Set<String>>();

    static {
        Set<String> viewGroup = new HashSet<String>(Arrays.asList(
                ATTR_LAYOUT_WIDTH,
                ATTR_LAYOUT_HEIGHT
        ));

        Set<String> margin = new HashSet<String>(viewGroup);
        margin.addAll(Arrays.asList(
                "layout_margin",
                "layout_marginBottom",
                "layout_marginLeft",
                "layout_marginRight",
                "layout_marginTop",
                "layout_marginStart",
                "layout_marginEnd",
                "layout_marginHorizontal",
                "layout_marginVertical"
        ));

        registerLayout("LinearLayout",
                combine(margin, "layout_weight", "layout_gravity"));

        registerLayout("RelativeLayout",
                combine(margin,
                        "layout_above",
                        "layout_alignBaseline",
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
                        "layout_below",
                        "layout_centerHorizontal",
                        "layout_centerInParent",
                        "layout_centerVertical",
                        "layout_toEndOf",
                        "layout_toLeftOf",
                        "layout_toRightOf",
                        "layout_toStartOf"));

        registerLayout("FrameLayout",
                combine(margin, "layout_gravity"));

        registerLayout("GridLayout",
                combine(margin,
                        "layout_column",
                        "layout_columnSpan",
                        "layout_columnWeight",
                        "layout_gravity",
                        "layout_row",
                        "layout_rowSpan",
                        "layout_rowWeight"));

        registerLayout("TableLayout",
                combine(margin, "layout_column", "layout_span"));

        registerLayout("TableRow",
                combine(margin, "layout_column", "layout_span"));

        registerLayout("DrawerLayout",
                combine(margin, "layout_gravity"),
                "android.support.v4.widget.DrawerLayout",
                "androidx.drawerlayout.widget.DrawerLayout");

        registerLayout("SlidingPaneLayout",
                combine(margin, "layout_weight"),
                "android.support.v4.widget.SlidingPaneLayout",
                "androidx.slidingpanelayout.widget.SlidingPaneLayout");

        registerLayout("Toolbar",
                combine(margin, "layout_gravity", "layout_weight"),
                "android.support.v7.widget.Toolbar",
                "androidx.appcompat.widget.Toolbar");

        registerLayout("ViewPager",
                combine(margin, "layout_gravity", "layout_isDecor"),
                "android.support.v4.view.ViewPager",
                "androidx.viewpager.widget.ViewPager");

        registerLayout("CoordinatorLayout",
                combine(margin,
                        "layout_anchor",
                        "layout_anchorGravity",
                        "layout_behavior",
                        "layout_dodgeInsetEdges",
                        "layout_insetEdge",
                        "layout_keyline"),
                "android.support.design.widget.CoordinatorLayout",
                "androidx.coordinatorlayout.widget.CoordinatorLayout");

        registerLayout("AppBarLayout",
                combine(margin, "layout_scrollFlags", "layout_scrollInterpolator"),
                "android.support.design.widget.AppBarLayout",
                "com.google.android.material.appbar.AppBarLayout");

        registerLayout("CollapsingToolbarLayout",
                combine(margin, "layout_collapseMode", "layout_collapseParallaxMultiplier"),
                "android.support.design.widget.CollapsingToolbarLayout",
                "com.google.android.material.appbar.CollapsingToolbarLayout");

        registerLayout("ConstraintLayout",
                combine(margin,
                        "layout_constraintBottom_toBottomOf",
                        "layout_constraintBottom_toTopOf",
                        "layout_constraintEnd_toEndOf",
                        "layout_constraintEnd_toStartOf",
                        "layout_constraintLeft_toLeftOf",
                        "layout_constraintLeft_toRightOf",
                        "layout_constraintRight_toLeftOf",
                        "layout_constraintRight_toRightOf",
                        "layout_constraintStart_toEndOf",
                        "layout_constraintStart_toStartOf",
                        "layout_constraintTop_toBottomOf",
                        "layout_constraintTop_toTopOf",
                        "layout_constraintBaseline_toBaselineOf",
                        "layout_constraintCircle",
                        "layout_constraintCircleAngle",
                        "layout_constraintCircleRadius",
                        "layout_constraintDimensionRatio",
                        "layout_constraintGuide_begin",
                        "layout_constraintGuide_end",
                        "layout_constraintGuide_percent",
                        "layout_constraintHeight_default",
                        "layout_constraintHeight_max",
                        "layout_constraintHeight_min",
                        "layout_constraintHeight_percent",
                        "layout_constraintHorizontal_bias",
                        "layout_constraintHorizontal_chainStyle",
                        "layout_constraintHorizontal_weight",
                        "layout_constraintTag",
                        "layout_constraintVertical_bias",
                        "layout_constraintVertical_chainStyle",
                        "layout_constraintVertical_weight",
                        "layout_constraintWidth_default",
                        "layout_constraintWidth_max",
                        "layout_constraintWidth_min",
                        "layout_constraintWidth_percent",
                        "layout_editor_absoluteX",
                        "layout_editor_absoluteY",
                        "layout_goneMarginBottom",
                        "layout_goneMarginEnd",
                        "layout_goneMarginLeft",
                        "layout_goneMarginRight",
                        "layout_goneMarginStart",
                        "layout_goneMarginTop"),
                "android.support.constraint.ConstraintLayout",
                "androidx.constraintlayout.widget.ConstraintLayout");

        registerLayout("MotionLayout",
                ATTRIBUTES_BY_TAG.get("androidx.constraintlayout.widget.ConstraintLayout"),
                "androidx.constraintlayout.motion.widget.MotionLayout");
    }

    private static Set<String> combine(Set<String> base, String... extras) {
        Set<String> result = new HashSet<String>(base);
        result.addAll(Arrays.asList(extras));
        return result;
    }

    private static void registerLayout(String simpleClassName, Set<String> attrs,
            String... additionalFqns) {
        ATTRIBUTES_BY_TAG.put(simpleClassName, attrs);
        ATTRIBUTES_BY_TAG.put("android.widget." + simpleClassName, attrs);
        for (String fqn : additionalFqns) {
            ATTRIBUTES_BY_TAG.put(fqn, attrs);
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!(element.getParentNode() instanceof Element)) {
            return;
        }

        Element parent = (Element) element.getParentNode();
        String parentTag = parent.getTagName();
        if (parentTag == null || "merge".equals(parentTag)) {
            return;
        }

        Set<String> supported = ATTRIBUTES_BY_TAG.get(parentTag);
        if (supported == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null || !name.startsWith(ATTR_LAYOUT_PREFIX)) {
                continue;
            }

            String namespace = attr.getNamespaceURI();
            if (TOOLS_URI.equals(namespace)) {
                continue;
            }

            if (!supported.contains(name)) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        String.format("Invalid layout param in a `%1$s`: `%2$s`",
                                parentTag, name));
            }
        }
    }
}