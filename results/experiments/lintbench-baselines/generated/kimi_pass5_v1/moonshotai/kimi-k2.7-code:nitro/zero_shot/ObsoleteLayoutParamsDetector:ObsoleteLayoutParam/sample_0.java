package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ObsoleteLayoutParamsDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no "
                    + "effect. This usually happens when you change the parent layout or move view "
                    + "code around without updating the layout params. This will cause useless "
                    + "attribute processing at runtime, and is misleading for others reading the "
                    + "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    private static final Map<String, Set<String>> sLayoutMap = new HashMap<>();
    private static final Set<String> sMarginAttrs = new HashSet<>();

    static {
        sMarginAttrs.add("layout_margin");
        sMarginAttrs.add("layout_marginLeft");
        sMarginAttrs.add("layout_marginTop");
        sMarginAttrs.add("layout_marginRight");
        sMarginAttrs.add("layout_marginBottom");
        sMarginAttrs.add("layout_marginStart");
        sMarginAttrs.add("layout_marginEnd");
        sMarginAttrs.add("layout_marginHorizontal");
        sMarginAttrs.add("layout_marginVertical");

        Set<String> frame = marginBase();
        frame.add("layout_gravity");
        add("FrameLayout", frame);
        add("FragmentContainerView", frame);
        add("NestedScrollView", frame);
        add("HorizontalScrollView", frame);
        add("ScrollView", frame);
        add("CardView", frame);

        Set<String> linear = marginBase();
        linear.add("layout_weight");
        linear.add("layout_gravity");
        add("LinearLayout", linear);
        add("RadioGroup", linear);
        add("Toolbar", linear);
        add("SearchView", linear);

        Set<String> relative = marginBase();
        relative.addAll(Arrays.asList(
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
                "layout_toStartOf"
        ));
        add("RelativeLayout", relative);

        Set<String> grid = marginBase();
        grid.addAll(Arrays.asList(
                "layout_gravity",
                "layout_column",
                "layout_columnSpan",
                "layout_row",
                "layout_rowSpan"
        ));
        add("GridLayout", grid);

        Set<String> tableRow = marginBase();
        tableRow.addAll(Arrays.asList(
                "layout_weight",
                "layout_gravity",
                "layout_column",
                "layout_span"
        ));
        add("TableRow", tableRow);

        Set<String> table = marginBase();
        table.addAll(Arrays.asList(
                "layout_weight",
                "layout_gravity",
                "layout_column",
                "layout_span"
        ));
        add("TableLayout", table);

        Set<String> absolute = base();
        absolute.addAll(Arrays.asList("layout_x", "layout_y"));
        add("AbsoluteLayout", absolute);

        Set<String> drawer = marginBase();
        drawer.add("layout_gravity");
        add("DrawerLayout", drawer);

        Set<String> coordinator = marginBase();
        coordinator.addAll(Arrays.asList(
                "layout_gravity",
                "layout_anchor",
                "layout_anchorGravity",
                "layout_behavior",
                "layout_dodgeInsetEdges",
                "layout_insetEdge",
                "layout_keyline"
        ));
        add("CoordinatorLayout", coordinator);

        Set<String> appBar = marginBase();
        appBar.add("layout_gravity");
        appBar.addAll(Arrays.asList("layout_scrollFlags", "layout_scrollInterpolator"));
        add("AppBarLayout", appBar);

        Set<String> collapsing = marginBase();
        collapsing.add("layout_gravity");
        collapsing.addAll(Arrays.asList(
                "layout_collapseMode",
                "layout_collapseParallaxMultiplier"
        ));
        add("CollapsingToolbarLayout", collapsing);

        Set<String> viewPager = marginBase();
        viewPager.addAll(Arrays.asList("layout_gravity", "layout_isDecor"));
        add("ViewPager", viewPager);

        Set<String> slidingPane = marginBase();
        slidingPane.add("layout_weight");
        add("SlidingPaneLayout", slidingPane);

        Set<String> recycler = marginBase();
        add("RecyclerView", recycler);

        Set<String> swipe = marginBase();
        add("SwipeRefreshLayout", swipe);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Detector.XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!(element.getParentNode() instanceof Element)) {
            return;
        }

        Element parent = (Element) element.getParentNode();
        String parentTag = parent.getTagName();
        if (parentTag == null) {
            return;
        }

        int dotIndex = parentTag.lastIndexOf('.');
        String parentName = dotIndex == -1 ? parentTag : parentTag.substring(dotIndex + 1);

        Set<String> validParams = sLayoutMap.get(parentName);
        if (validParams == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            if (!ANDROID_URI.equals(attr.getNamespaceURI())) {
                continue;
            }

            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
            }

            if (name.startsWith("layout_") && !validParams.contains(name)) {
                String message = String.format(
                        "The layout param '%1$s' is not defined for the parent layout <%2$s>; it will not have any effect on this view.",
                        name, parentName);
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }

    private static Set<String> base() {
        Set<String> set = new HashSet<>();
        set.add("layout_width");
        set.add("layout_height");
        return set;
    }

    private static Set<String> marginBase() {
        Set<String> set = base();
        set.addAll(sMarginAttrs);
        return set;
    }

    private static void add(String tag, Set<String> attrs) {
        sLayoutMap.put(tag, new HashSet<>(attrs));
    }
}