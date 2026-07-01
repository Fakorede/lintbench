package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final String ISSUE_ID = "ObsoleteLayoutParam";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no "
                    + "effect. This usually happens when you change the parent layout or move "
                    + "view code around without updating the layout params. This will cause "
                    + "useless attribute processing at runtime, and is misleading for others "
                    + "reading the layout so the parameter should be removed.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Multimap<String, String> VALID_LAYOUTS_BY_PARAM =
            ArrayListMultimap.create();

    private static final Map<String, String> CLASS_ALIASES = new HashMap<>();

    static {
        add("AbsoluteLayout", "layout_x");
        add("AbsoluteLayout", "layout_y");

        add("FrameLayout", "layout_gravity");

        add("LinearLayout", "layout_gravity");
        add("LinearLayout", "layout_weight");

        add("RelativeLayout", "layout_alignBaseline");
        add("RelativeLayout", "layout_alignBottom");
        add("RelativeLayout", "layout_alignEnd");
        add("RelativeLayout", "layout_alignLeft");
        add("RelativeLayout", "layout_alignParentBottom");
        add("RelativeLayout", "layout_alignParentEnd");
        add("RelativeLayout", "layout_alignParentLeft");
        add("RelativeLayout", "layout_alignParentRight");
        add("RelativeLayout", "layout_alignParentStart");
        add("RelativeLayout", "layout_alignParentTop");
        add("RelativeLayout", "layout_alignRight");
        add("RelativeLayout", "layout_alignStart");
        add("RelativeLayout", "layout_alignTop");
        add("RelativeLayout", "layout_alignWithParentIfMissing");
        add("RelativeLayout", "layout_below");
        add("RelativeLayout", "layout_centerHorizontal");
        add("RelativeLayout", "layout_centerInParent");
        add("RelativeLayout", "layout_centerVertical");
        add("RelativeLayout", "layout_toEndOf");
        add("RelativeLayout", "layout_toLeftOf");
        add("RelativeLayout", "layout_toRightOf");
        add("RelativeLayout", "layout_toStartOf");

        add("GridLayout", "layout_column");
        add("GridLayout", "layout_columnSpan");
        add("GridLayout", "layout_gravity");
        add("GridLayout", "layout_row");
        add("GridLayout", "layout_rowSpan");

        add("TableLayout", "layout_column");
        add("TableLayout", "layout_span");

        add("TableRow", "layout_column");
        add("TableRow", "layout_span");

        add("DrawerLayout", "layout_gravity");

        add("SlidingPaneLayout", "layout_weight");

        add("CoordinatorLayout", "layout_anchor");
        add("CoordinatorLayout", "layout_anchorGravity");
        add("CoordinatorLayout", "layout_behavior");
        add("CoordinatorLayout", "layout_dodgeInsetEdges");
        add("CoordinatorLayout", "layout_insetEdge");
        add("CoordinatorLayout", "layout_keyline");

        add("CollapsingToolbarLayout", "layout_collapseMode");
        add("CollapsingToolbarLayout", "layout_collapseParallaxMultiplier");

        add("PercentFrameLayout", "layout_aspectRatio");
        add("PercentFrameLayout", "layout_heightPercent");
        add("PercentFrameLayout", "layout_marginBottomPercent");
        add("PercentFrameLayout", "layout_marginEndPercent");
        add("PercentFrameLayout", "layout_marginLeftPercent");
        add("PercentFrameLayout", "layout_marginPercent");
        add("PercentFrameLayout", "layout_marginRightPercent");
        add("PercentFrameLayout", "layout_marginStartPercent");
        add("PercentFrameLayout", "layout_marginTopPercent");
        add("PercentFrameLayout", "layout_widthPercent");

        add("PercentRelativeLayout", "layout_aspectRatio");
        add("PercentRelativeLayout", "layout_heightPercent");
        add("PercentRelativeLayout", "layout_widthPercent");

        add("FlexboxLayout", "layout_alignSelf");
        add("FlexboxLayout", "layout_flexBasisPercent");
        add("FlexboxLayout", "layout_flexGrow");
        add("FlexboxLayout", "layout_flexShrink");
        add("FlexboxLayout", "layout_maxHeight");
        add("FlexboxLayout", "layout_maxWidth");
        add("FlexboxLayout", "layout_minHeight");
        add("FlexboxLayout", "layout_minWidth");
        add("FlexboxLayout", "layout_order");
        add("FlexboxLayout", "layout_wrapBefore");

        alias("android.support.v4.widget.DrawerLayout", "DrawerLayout");
        alias("android.support.v4.widget.SlidingPaneLayout", "SlidingPaneLayout");
        alias("android.support.v7.widget.GridLayout", "GridLayout");
        alias("android.support.percent.PercentFrameLayout", "PercentFrameLayout");
        alias("android.support.percent.PercentRelativeLayout", "PercentRelativeLayout");
        alias("android.support.design.widget.CoordinatorLayout", "CoordinatorLayout");
        alias("android.support.design.widget.CollapsingToolbarLayout", "CollapsingToolbarLayout");
        alias("com.google.android.flexbox.FlexboxLayout", "FlexboxLayout");
        alias("com.google.android.material.widget.CoordinatorLayout", "CoordinatorLayout");
        alias("com.google.android.material.appbar.CollapsingToolbarLayout", "CollapsingToolbarLayout");
    }

    private static void add(String layoutClass, String paramName) {
        VALID_LAYOUTS_BY_PARAM.put(paramName, layoutClass);
    }

    private static void alias(String className, String aliasName) {
        CLASS_ALIASES.put(className, aliasName);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return VALID_LAYOUTS_BY_PARAM.keySet();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String paramName = attribute.getLocalName();
        Collection<String> validLayouts = VALID_LAYOUTS_BY_PARAM.get(paramName);
        if (validLayouts == null || validLayouts.isEmpty()) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Element parent = getParentElement(element);
        if (parent == null) {
            return;
        }

        String parentTag = parent.getTagName();
        String parentClass = getLayoutClassName(parentTag);
        if (parentClass == null) {
            return;
        }

        if ("merge".equals(parentClass) || "include".equals(parentClass)) {
            return;
        }

        if (validLayouts.contains(parentClass)) {
            return;
        }

        String aliased = CLASS_ALIASES.get(parentClass);
        if (aliased != null && validLayouts.contains(aliased)) {
            return;
        }

        String message = String.format(
                "Invalid layout param in a `%1$s`: `%2$s`",
                parentTag, attribute.getName());

        LintFix fix = LintFix.create()
                .removeAttr(ANDROID_URI, paramName)
                .build();

        context.report(ISSUE, attribute, context.getLocation(attribute), message, fix);
    }

    @Nullable
    private static Element getParentElement(@NonNull Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        return parent instanceof Element ? (Element) parent : null;
    }

    @Nullable
    private static String getLayoutClassName(@NonNull String tagName) {
        if (tagName.indexOf('.') == -1) {
            return tagName;
        }
        int index = tagName.lastIndexOf('.');
        return tagName.substring(index + 1);
    }
}