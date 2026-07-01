package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    private static final Map<String, List<String>> ALLOWED_PARAMS = new HashMap<>();

    static {
        ALLOWED_PARAMS.put("LinearLayout", Arrays.asList("layout_weight", "layout_gravity"));
        ALLOWED_PARAMS.put("RelativeLayout", Arrays.asList("layout_align", "layout_center", "layout_to", "layout_above", "layout_below", "layout_baseline", "layout_alignWithParentIfMissing"));
        ALLOWED_PARAMS.put("FrameLayout", Arrays.asList("layout_gravity"));
        ALLOWED_PARAMS.put("ConstraintLayout", Arrays.asList("layout_constraint", "layout_goneMargin", "layout_editor_absolute", "layout_wrapBehaviorInParent"));
        ALLOWED_PARAMS.put("MotionLayout", Arrays.asList("layout_constraint", "layout_goneMargin", "layout_editor_absolute", "layout_wrapBehaviorInParent"));
        ALLOWED_PARAMS.put("GridLayout", Arrays.asList("layout_row", "layout_column", "layout_gravity"));
        ALLOWED_PARAMS.put("CoordinatorLayout", Arrays.asList("layout_anchor", "layout_anchorGravity", "layout_behavior", "layout_keyline", "layout_insetEdge", "layout_dodgeInsetEdges"));
        ALLOWED_PARAMS.put("DrawerLayout", Arrays.asList("layout_gravity"));
        ALLOWED_PARAMS.put("TableRow", Arrays.asList("layout_column", "layout_span"));
        ALLOWED_PARAMS.put("TableLayout", Arrays.asList("layout_column", "layout_span", "layout_gravity", "layout_weight"));
        ALLOWED_PARAMS.put("ScrollView", Arrays.asList("layout_gravity"));
        ALLOWED_PARAMS.put("HorizontalScrollView", Arrays.asList("layout_gravity"));
        ALLOWED_PARAMS.put("NestedScrollView", Arrays.asList("layout_gravity"));
        ALLOWED_PARAMS.put("RecyclerView", Collections.emptyList());
        ALLOWED_PARAMS.put("ListView", Collections.emptyList());
        ALLOWED_PARAMS.put("GridView", Collections.emptyList());
        ALLOWED_PARAMS.put("ViewPager", Collections.emptyList());
        ALLOWED_PARAMS.put("ViewPager2", Collections.emptyList());
        ALLOWED_PARAMS.put("Toolbar", Arrays.asList("layout_gravity"));
        ALLOWED_PARAMS.put("AppBarLayout", Arrays.asList("layout_scrollFlags"));
        ALLOWED_PARAMS.put("CollapsingToolbarLayout", Arrays.asList("layout_collapseMode", "layout_collapseParallaxMultiplier"));
        ALLOWED_PARAMS.put("SwipeRefreshLayout", Collections.emptyList());
        ALLOWED_PARAMS.put("PercentFrameLayout", Arrays.asList("layout_gravity", "layout_widthPercent", "layout_heightPercent", "layout_marginPercent", "layout_aspectRatio"));
        ALLOWED_PARAMS.put("PercentRelativeLayout", Arrays.asList("layout_align", "layout_center", "layout_to", "layout_above", "layout_below", "layout_baseline", "layout_widthPercent", "layout_heightPercent", "layout_marginPercent", "layout_aspectRatio"));
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = getLocalName(parent.getTagName());

        if ("merge".equals(parentTag) || "include".equals(parentTag) || "ViewStub".equals(parentTag)) {
            return;
        }

        List<String> allowed = ALLOWED_PARAMS.get(parentTag);
        if (allowed == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String attrName = getLocalName(attr.getName());

            if (!attrName.startsWith("layout_")) {
                continue;
            }
            if (attrName.equals("layout_width") || attrName.equals("layout_height")) {
                continue;
            }
            if (attrName.startsWith("layout_margin")) {
                continue;
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
                context.report(ISSUE, context.getLocation(attr), message);
            }
        }
    }

    private static String getLocalName(String name) {
        int colon = name.indexOf(':');
        if (colon != -1) {
            name = name.substring(colon + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(dot + 1);
        }
        return name;
    }
}