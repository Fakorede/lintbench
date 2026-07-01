package com.android.tools.lint.checks;

import static com.android.SdkConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "ObsoleteLayoutParam",
        "Obsolete layout params",
        "The given layout_param is not defined for the given layout, meaning it has no "
            + "effect. This usually happens when you change the parent layout or move view "
            + "code around without updating the layout params. This will cause useless "
            + "attribute processing at runtime, and is misleading for others reading the "
            + "layout so the parameter should be removed.",
        Category.CORRECTNESS,
        4,
        Severity.WARNING,
        new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE)
    );

    private static final Map<String, Set<String>> VALID_PARENTS = new HashMap<>();

    static {
        String[] relativeLayouts = {"RelativeLayout", "PercentRelativeLayout"};
        add("layout_above", relativeLayouts);
        add("layout_alignBaseline", relativeLayouts);
        add("layout_alignBottom", relativeLayouts);
        add("layout_alignEnd", relativeLayouts);
        add("layout_alignLeft", relativeLayouts);
        add("layout_alignParentBottom", relativeLayouts);
        add("layout_alignParentEnd", relativeLayouts);
        add("layout_alignParentLeft", relativeLayouts);
        add("layout_alignParentRight", relativeLayouts);
        add("layout_alignParentStart", relativeLayouts);
        add("layout_alignParentTop", relativeLayouts);
        add("layout_alignRight", relativeLayouts);
        add("layout_alignStart", relativeLayouts);
        add("layout_alignTop", relativeLayouts);
        add("layout_alignWithParentIfMissing", relativeLayouts);
        add("layout_below", relativeLayouts);
        add("layout_centerHorizontal", relativeLayouts);
        add("layout_centerInParent", relativeLayouts);
        add("layout_centerVertical", relativeLayouts);
        add("layout_toEndOf", relativeLayouts);
        add("layout_toLeftOf", relativeLayouts);
        add("layout_toRightOf", relativeLayouts);
        add("layout_toStartOf", relativeLayouts);

        add("layout_weight", "LinearLayout", "RadioGroup", "TableRow", "TableLayout",
                "AppBarLayout", "SlidingPaneLayout");

        add("layout_gravity",
                "FrameLayout", "ViewAnimator", "ViewFlipper", "ViewSwitcher",
                "ScrollView", "HorizontalScrollView", "NestedScrollView", "CardView",
                "PercentFrameLayout", "CalendarView", "DatePicker", "TimePicker",
                "GestureOverlayView", "MediaController", "FragmentContainerView",
                "LinearLayout", "RadioGroup", "TableRow", "TableLayout", "AppBarLayout",
                "GridLayout", "DrawerLayout", "CoordinatorLayout", "CollapsingToolbarLayout",
                "Toolbar", "SlidingPaneLayout");

        add("layout_column", "TableRow", "GridLayout");
        add("layout_span", "TableRow");
        add("layout_columnSpan", "GridLayout");
        add("layout_row", "GridLayout");
        add("layout_rowSpan", "GridLayout");

        add("layout_scrollFlags", "AppBarLayout");
        add("layout_scrollInterpolator", "AppBarLayout");

        add("layout_collapseMode", "CollapsingToolbarLayout");
        add("layout_collapseParallaxMultiplier", "CollapsingToolbarLayout");

        add("layout_anchor", "CoordinatorLayout");
        add("layout_anchorGravity", "CoordinatorLayout");
        add("layout_behavior", "CoordinatorLayout");
        add("layout_dodgeInsetEdges", "CoordinatorLayout");
        add("layout_insetEdge", "CoordinatorLayout");
        add("layout_keyline", "CoordinatorLayout");

        String[] constraintLayouts = {"ConstraintLayout", "MotionLayout"};
        add("layout_constraintBaseline_toBaselineOf", constraintLayouts);
        add("layout_constraintBaseline_toBottomOf", constraintLayouts);
        add("layout_constraintBaseline_toTopOf", constraintLayouts);
        add("layout_constraintBottom_toBottomOf", constraintLayouts);
        add("layout_constraintBottom_toTopOf", constraintLayouts);
        add("layout_constraintCircle", constraintLayouts);
        add("layout_constraintCircleAngle", constraintLayouts);
        add("layout_constraintCircleRadius", constraintLayouts);
        add("layout_constraintDimensionRatio", constraintLayouts);
        add("layout_constraintEnd_toEndOf", constraintLayouts);
        add("layout_constraintEnd_toStartOf", constraintLayouts);
        add("layout_constraintGuide_begin", constraintLayouts);
        add("layout_constraintGuide_end", constraintLayouts);
        add("layout_constraintGuide_percent", constraintLayouts);
        add("layout_constraintHeight_default", constraintLayouts);
        add("layout_constraintHeight_max", constraintLayouts);
        add("layout_constraintHeight_min", constraintLayouts);
        add("layout_constraintHeight_percent", constraintLayouts);
        add("layout_constraintHorizontal_bias", constraintLayouts);
        add("layout_constraintHorizontal_chainStyle", constraintLayouts);
        add("layout_constraintHorizontal_weight", constraintLayouts);
        add("layout_constraintLeft_creator", constraintLayouts);
        add("layout_constraintLeft_toLeftOf", constraintLayouts);
        add("layout_constraintLeft_toRightOf", constraintLayouts);
        add("layout_constraintRight_creator", constraintLayouts);
        add("layout_constraintRight_toLeftOf", constraintLayouts);
        add("layout_constraintRight_toRightOf", constraintLayouts);
        add("layout_constraintStart_toEndOf", constraintLayouts);
        add("layout_constraintStart_toStartOf", constraintLayouts);
        add("layout_constraintTag", constraintLayouts);
        add("layout_constraintTop_creator", constraintLayouts);
        add("layout_constraintTop_toBottomOf", constraintLayouts);
        add("layout_constraintTop_toTopOf", constraintLayouts);
        add("layout_constraintVertical_bias", constraintLayouts);
        add("layout_constraintVertical_chainStyle", constraintLayouts);
        add("layout_constraintVertical_weight", constraintLayouts);
        add("layout_constraintWidth_default", constraintLayouts);
        add("layout_constraintWidth_max", constraintLayouts);
        add("layout_constraintWidth_min", constraintLayouts);
        add("layout_constraintWidth_percent", constraintLayouts);
        add("layout_editor_absoluteX", constraintLayouts);
        add("layout_editor_absoluteY", constraintLayouts);
        add("layout_goneMarginBottom", constraintLayouts);
        add("layout_goneMarginEnd", constraintLayouts);
        add("layout_goneMarginLeft", constraintLayouts);
        add("layout_goneMarginRight", constraintLayouts);
        add("layout_goneMarginStart", constraintLayouts);
        add("layout_goneMarginTop", constraintLayouts);
        add("layout_constrainedHeight", constraintLayouts);
        add("layout_constrainedWidth", constraintLayouts);
        add("layout_wrapBehaviorInParent", constraintLayouts);

        add("layout_alignSelf", "FlexboxLayout");
        add("layout_flexGrow", "FlexboxLayout");
        add("layout_flexShrink", "FlexboxLayout");
        add("layout_maxHeight", "FlexboxLayout");
        add("layout_maxWidth", "FlexboxLayout");
        add("layout_minHeight", "FlexboxLayout");
        add("layout_minWidth", "FlexboxLayout");
        add("layout_order", "FlexboxLayout");
        add("layout_wrapBefore", "FlexboxLayout");

        String[] percentLayouts = {"PercentFrameLayout", "PercentRelativeLayout"};
        add("layout_widthPercent", percentLayouts);
        add("layout_heightPercent", percentLayouts);
        add("layout_marginPercent", percentLayouts);
        add("layout_marginLeftPercent", percentLayouts);
        add("layout_marginStartPercent", percentLayouts);
        add("layout_marginRightPercent", percentLayouts);
        add("layout_marginEndPercent", percentLayouts);
        add("layout_marginTopPercent", percentLayouts);
        add("layout_marginBottomPercent", percentLayouts);
        add("layout_aspectRatio", percentLayouts);
    }

    private static void add(String attribute, String... parents) {
        VALID_PARENTS.computeIfAbsent(attribute, k -> new HashSet<>()).addAll(Arrays.asList(parents));
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return new ArrayList<>(VALID_PARENTS.keySet());
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if (TOOLS_URI.equals(namespace)) {
            return;
        }

        String name = getLocalName(attribute);
        if (name == null) {
            return;
        }

        Set<String> validParents = VALID_PARENTS.get(name);
        if (validParents == null) {
            return;
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
        String parentTag = getSimpleTagName(parent);
        if (parentTag == null) {
            return;
        }

        if ("merge".equals(parentTag) || "include".equals(parentTag)) {
            return;
        }

        if (!validParents.contains(parentTag)) {
            String message = String.format(
                    "The `%1$s` attribute is not defined for `%2$s` and will not have any effect",
                    attribute.getName(), parentTag);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private static String getLocalName(Attr attribute) {
        String name = attribute.getLocalName();
        if (name != null) {
            return name;
        }
        name = attribute.getName();
        if (name == null) {
            return null;
        }
        int index = name.indexOf(':');
        return index != -1 ? name.substring(index + 1) : name;
    }

    private static String getSimpleTagName(Element element) {
        String name = element.getLocalName();
        if (name == null) {
            name = element.getTagName();
        }
        if (name == null) {
            return null;
        }
        int index = name.lastIndexOf('.');
        return index != -1 ? name.substring(index + 1) : name;
    }
}