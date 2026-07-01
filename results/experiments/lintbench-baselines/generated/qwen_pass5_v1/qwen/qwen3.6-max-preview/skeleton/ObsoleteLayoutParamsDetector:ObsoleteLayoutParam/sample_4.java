package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no effect. " +
                    "This usually happens when you change the parent layout or move view code around without " +
                    "updating the layout params. This will cause useless attribute processing at runtime, " +
                    "and is misleading for others reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Map<String, Set<String>> VALID_PARENTS = new HashMap<>();

    static {
        add("layout_weight", "LinearLayout");
        add("layout_gravity", "LinearLayout", "FrameLayout", "CoordinatorLayout", "DrawerLayout", "Toolbar", "AppBarLayout", "GridLayout");

        String[] rlParams = {
            "layout_alignParentTop", "layout_alignParentBottom", "layout_alignParentLeft",
            "layout_alignParentRight", "layout_alignParentStart", "layout_alignParentEnd",
            "layout_centerHorizontal", "layout_centerVertical", "layout_centerInParent",
            "layout_toRightOf", "layout_toLeftOf", "layout_toStartOf", "layout_toEndOf",
            "layout_above", "layout_below", "layout_alignBaseline", "layout_alignTop",
            "layout_alignBottom", "layout_alignLeft", "layout_alignRight", "layout_alignStart", "layout_alignEnd"
        };
        for (String p : rlParams) add(p, "RelativeLayout");

        String[] glParams = {
            "layout_column", "layout_row", "layout_columnSpan", "layout_rowSpan",
            "layout_columnWeight", "layout_rowWeight"
        };
        for (String p : glParams) add(p, "GridLayout");

        add("layout_anchor", "CoordinatorLayout");
        add("layout_anchorGravity", "CoordinatorLayout");
        add("layout_behavior", "CoordinatorLayout");
        add("layout_insetEdge", "CoordinatorLayout");
        add("layout_dodgeInsetEdges", "CoordinatorLayout");
        add("layout_keyline", "CoordinatorLayout");

        add("layout_scrollFlags", "AppBarLayout");
        add("layout_scrollInterpolator", "AppBarLayout");
    }

    private static void add(String attr, String... parents) {
        VALID_PARENTS.put(attr, new HashSet<>(Arrays.asList(parents)));
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null || !localName.startsWith("layout_")) {
            return;
        }

        // Skip universally supported layout parameters
        if (localName.equals("layout_width") || localName.equals("layout_height") ||
            localName.startsWith("layout_margin") || localName.equals("layout_description") ||
            localName.equals("layout_mode") || localName.equals("layoutDirection") ||
            localName.equals("layoutAnimation")) {
            return;
        }

        Node owner = attribute.getOwnerElement();
        if (owner == null) return;
        
        Node parentNode = owner.getParentNode();
        if (!(parentNode instanceof Element)) return;

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();
        String simpleParentTag = parentTag.substring(parentTag.lastIndexOf('.') + 1);

        if (!isValidParamForParent(localName, simpleParentTag)) {
            String message = String.format(
                "Invalid layout param `%1$s` in a `%2$s`", attribute.getName(), simpleParentTag);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private boolean isValidParamForParent(String attrName, String parentSimpleName) {
        Set<String> valid = VALID_PARENTS.get(attrName);
        if (valid != null) {
            return valid.contains(parentSimpleName);
        }

        // ConstraintLayout parameters
        if (attrName.startsWith("layout_constraint") || attrName.startsWith("layout_goneMargin") ||
            attrName.equals("layout_editor_absoluteX") || attrName.equals("layout_editor_absoluteY") ||
            attrName.equals("layout_constraintHorizontal_bias") || attrName.equals("layout_constraintVertical_bias") ||
            attrName.equals("layout_constraintDimensionRatio") || attrName.equals("layout_constraintCircle") ||
            attrName.equals("layout_constraintCircleRadius") || attrName.equals("layout_constraintCircleAngle") ||
            attrName.equals("layout_optimizationLevel")) {
            return parentSimpleName.equals("ConstraintLayout") || parentSimpleName.equals("MotionLayout");
        }

        // FlexboxLayout parameters
        if (attrName.startsWith("layout_flex") || attrName.equals("layout_order") || attrName.equals("layout_alignSelf")) {
            return parentSimpleName.equals("FlexboxLayout");
        }

        // MotionLayout specific parameters
        if (attrName.startsWith("layout_motion")) {
            return parentSimpleName.equals("MotionLayout") || parentSimpleName.equals("ConstraintLayout");
        }

        // Unknown layout params are assumed valid to avoid false positives on custom ViewGroup implementations
        return true;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Attribute visitation handles the validation logic
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No post-processing required
    }
}