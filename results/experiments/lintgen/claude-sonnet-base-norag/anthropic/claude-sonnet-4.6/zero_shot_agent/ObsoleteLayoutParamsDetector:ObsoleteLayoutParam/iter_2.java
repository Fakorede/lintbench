package com.android.tools.lint.checks;

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
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final Map<String, Set<String>> PARAM_TO_LAYOUTS = new HashMap<>();
    private static final Set<String> KNOWN_LAYOUTS = new HashSet<>();

    static {
        registerParam("layout_x", "AbsoluteLayout");
        registerParam("layout_y", "AbsoluteLayout");

        registerParam("layout_weight", "LinearLayout", "RadioGroup");

        registerParam("layout_gravity",
                "LinearLayout", "RadioGroup",
                "GridLayout",
                "FrameLayout",
                "ScrollView", "HorizontalScrollView");

        registerParam("layout_above", "RelativeLayout");
        registerParam("layout_below", "RelativeLayout");
        registerParam("layout_toLeftOf", "RelativeLayout");
        registerParam("layout_toRightOf", "RelativeLayout");
        registerParam("layout_toStartOf", "RelativeLayout");
        registerParam("layout_toEndOf", "RelativeLayout");
        registerParam("layout_alignTop", "RelativeLayout");
        registerParam("layout_alignBottom", "RelativeLayout");
        registerParam("layout_alignLeft", "RelativeLayout");
        registerParam("layout_alignRight", "RelativeLayout");
        registerParam("layout_alignStart", "RelativeLayout");
        registerParam("layout_alignEnd", "RelativeLayout");
        registerParam("layout_alignBaseline", "RelativeLayout");
        registerParam("layout_alignParentTop", "RelativeLayout");
        registerParam("layout_alignParentBottom", "RelativeLayout");
        registerParam("layout_alignParentLeft", "RelativeLayout");
        registerParam("layout_alignParentRight", "RelativeLayout");
        registerParam("layout_alignParentStart", "RelativeLayout");
        registerParam("layout_alignParentEnd", "RelativeLayout");
        registerParam("layout_centerInParent", "RelativeLayout");
        registerParam("layout_centerHorizontal", "RelativeLayout");
        registerParam("layout_centerVertical", "RelativeLayout");

        registerParam("layout_row", "GridLayout");
        registerParam("layout_rowSpan", "GridLayout");
        registerParam("layout_rowWeight", "GridLayout");
        registerParam("layout_column", "GridLayout", "TableRow");
        registerParam("layout_columnSpan", "GridLayout");
        registerParam("layout_columnWeight", "GridLayout");

        registerParam("layout_span", "TableRow");

        KNOWN_LAYOUTS.addAll(Arrays.asList(
                "LinearLayout",
                "RelativeLayout",
                "FrameLayout",
                "TableLayout",
                "TableRow",
                "GridLayout",
                "AbsoluteLayout",
                "ScrollView",
                "HorizontalScrollView",
                "ListView",
                "GridView",
                "ExpandableListView",
                "RadioGroup",
                "SlidingDrawer",
                "ViewAnimator",
                "ViewFlipper",
                "ViewSwitcher",
                "TextSwitcher",
                "ImageSwitcher",
                "AdapterViewFlipper",
                "StackView",
                "CoordinatorLayout",
                "AppBarLayout",
                "CollapsingToolbarLayout",
                "ConstraintLayout",
                "DrawerLayout"
        ));
    }

    private static void registerParam(String param, String... layouts) {
        Set<String> layoutSet = PARAM_TO_LAYOUTS.computeIfAbsent(param, k -> new HashSet<>());
        for (String layout : layouts) {
            int dotIndex = layout.lastIndexOf('.');
            String simpleName = dotIndex >= 0 ? layout.substring(dotIndex + 1) : layout;
            layoutSet.add(simpleName);
        }
    }

    public ObsoleteLayoutParamsDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return PARAM_TO_LAYOUTS.keySet();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            if (name == null) {
                return;
            }
            int colon = name.indexOf(':');
            if (colon >= 0) {
                name = name.substring(colon + 1);
            }
        }

        Set<String> validParents = PARAM_TO_LAYOUTS.get(name);
        if (validParents == null || validParents.isEmpty()) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();
        if (parentTag == null) {
            return;
        }

        if (isMergeOrInclude(parentTag)) {
            return;
        }

        String parentSimpleName = parentTag;
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex >= 0) {
            parentSimpleName = parentTag.substring(dotIndex + 1);
        }

        if (validParents.contains(parentSimpleName) || validParents.contains(parentTag)) {
            return;
        }

        if (!KNOWN_LAYOUTS.contains(parentSimpleName) && !KNOWN_LAYOUTS.contains(parentTag)) {
            return;
        }

        context.report(ISSUE, attribute, context.getLocation(attribute),
                String.format("Invalid layout param `%1$s` (not defined by parent layout `%2$s`)",
                        name, parentTag));
    }

    private static boolean isMergeOrInclude(@NonNull String tag) {
        return tag.equals("merge") || tag.equals("include") || tag.equals("fragment")
                || tag.equals("requestFocus") || tag.equals("tag");
    }
}