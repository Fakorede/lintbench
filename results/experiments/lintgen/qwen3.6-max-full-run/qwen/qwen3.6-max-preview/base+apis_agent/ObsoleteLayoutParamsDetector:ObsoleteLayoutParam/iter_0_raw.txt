package com.android.tools.lint.checks;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    private static final Map<String, String[]> ALLOWED_PARAMS = new HashMap<>();

    static {
        ALLOWED_PARAMS.put("LinearLayout", new String[]{"layout_weight", "layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("RelativeLayout", new String[]{"layout_align", "layout_center", "layout_to", "layout_above", "layout_below", "layout_baseline", "layout_margin"});
        ALLOWED_PARAMS.put("FrameLayout", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("ConstraintLayout", new String[]{"layout_constraint", "layout_goneMargin", "layout_editor_absolute", "layout_optimization", "layout_wrapBehaviorInParent", "layout_margin"});
        ALLOWED_PARAMS.put("MotionLayout", new String[]{"layout_constraint", "layout_goneMargin", "layout_editor_absolute", "layout_optimization", "layout_wrapBehaviorInParent", "layout_margin"});
        ALLOWED_PARAMS.put("GridLayout", new String[]{"layout_row", "layout_column", "layout_rowSpan", "layout_columnSpan", "layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("CoordinatorLayout", new String[]{"layout_anchor", "layout_anchorGravity", "layout_behavior", "layout_keyline", "layout_insetEdge", "layout_dodgeInsetEdges", "layout_margin"});
        ALLOWED_PARAMS.put("DrawerLayout", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("TableRow", new String[]{"layout_column", "layout_span", "layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("TableLayout", new String[]{"layout_column", "layout_span", "layout_gravity", "layout_weight", "layout_margin"});
        ALLOWED_PARAMS.put("ScrollView", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("HorizontalScrollView", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("NestedScrollView", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("RecyclerView", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("ListView", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("GridView", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("ViewPager", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("ViewPager2", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("Toolbar", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("AppBarLayout", new String[]{"layout_scrollFlags", "layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("CollapsingToolbarLayout", new String[]{"layout_collapseMode", "layout_collapseParallaxMultiplier", "layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("SwipeRefreshLayout", new String[]{"layout_gravity", "layout_margin"});
        ALLOWED_PARAMS.put("PercentFrameLayout", new String[]{"layout_gravity", "layout_margin", "layout_widthPercent", "layout_heightPercent", "layout_marginPercent", "layout_aspectRatio"});
        ALLOWED_PARAMS.put("PercentRelativeLayout", new String[]{"layout_align", "layout_center", "layout_to", "layout_above", "layout_below", "layout_baseline", "layout_margin", "layout_widthPercent", "layout_heightPercent", "layout_marginPercent", "layout_aspectRatio"});
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String attrName = attribute.getLocalName();
        if (attrName == null) {
            attrName = attribute.getName();
        }
        if (attrName == null || !attrName.startsWith("layout_")) {
            return;
        }
        if (attrName.equals("layout_width") || attrName.equals("layout_height")) {
            return;
        }

        Node viewNode = attribute.getParentNode();
        if (!(viewNode instanceof Element)) {
            return;
        }
        Element view = (Element) viewNode;

        Node parentNode = view.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        int colon = parentTag.indexOf(':');
        if (colon != -1) {
            parentTag = parentTag.substring(colon + 1);
        }
        int dot = parentTag.lastIndexOf('.');
        if (dot != -1) {
            parentTag = parentTag.substring(dot + 1);
        }

        if ("merge".equals(parentTag)) {
            return;
        }

        String[] allowed = ALLOWED_PARAMS.get(parentTag);
        if (allowed == null) {
            return;
        }

        boolean valid = false;
        for (String prefix : allowed) {
            if (attrName.equals(prefix) || attrName.startsWith(prefix)) {
                valid = true;
                break;
            }
        }

        if (!valid) {
            String message = String.format("Invalid layout param in a `%s`: `%s`", parentTag, attrName);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}