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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no "
                    + "effect. This usually happens when you change the parent layout or move view "
                    + "code around without updating the layout params. This will cause useless "
                    + "attribute processing at runtime, and is misleading for others reading the "
                    + "layout so the parameter should be removed.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Map<String, List<String>> PARAM_PARENT_MAP = new HashMap<>();

    static {
        List<String> linear = Arrays.asList("LinearLayout");
        List<String> frame = Arrays.asList("FrameLayout");
        List<String> relative = Arrays.asList("RelativeLayout");
        List<String> grid = Arrays.asList("GridLayout");
        List<String> coordinator = Arrays.asList("CoordinatorLayout");
        List<String> appbar = Arrays.asList("AppBarLayout");

        PARAM_PARENT_MAP.put("layout_weight", linear);
        PARAM_PARENT_MAP.put("layout_gravity", Arrays.asList("LinearLayout", "FrameLayout"));
        PARAM_PARENT_MAP.put("layout_row", grid);
        PARAM_PARENT_MAP.put("layout_column", grid);
        PARAM_PARENT_MAP.put("layout_rowSpan", grid);
        PARAM_PARENT_MAP.put("layout_columnSpan", grid);
        PARAM_PARENT_MAP.put("layout_rowWeight", grid);
        PARAM_PARENT_MAP.put("layout_columnWeight", grid);
        PARAM_PARENT_MAP.put("layout_behavior", coordinator);
        PARAM_PARENT_MAP.put("layout_anchor", coordinator);
        PARAM_PARENT_MAP.put("layout_anchorGravity", coordinator);
        PARAM_PARENT_MAP.put("layout_insetEdge", coordinator);
        PARAM_PARENT_MAP.put("layout_dodgeInsetEdges", coordinator);
        PARAM_PARENT_MAP.put("layout_scrollFlags", appbar);
        PARAM_PARENT_MAP.put("layout_scrollEffect", appbar);

        List<String> relParams = Arrays.asList(
                "layout_alignParentTop", "layout_alignParentBottom", "layout_alignParentLeft",
                "layout_alignParentRight", "layout_alignParentStart", "layout_alignParentEnd",
                "layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical",
                "layout_toLeftOf", "layout_toRightOf", "layout_toStartOf", "layout_toEndOf",
                "layout_above", "layout_below", "layout_alignBaseline", "layout_alignTop",
                "layout_alignBottom", "layout_alignLeft", "layout_alignRight", "layout_alignStart",
                "layout_alignEnd", "layout_alignWithParentIfMissing"
        );
        for (String param : relParams) {
            PARAM_PARENT_MAP.put(param, relative);
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("layout_*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // State tracking not required for this implementation
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String attrName = attribute.getLocalName();
        if (attrName == null || !attrName.startsWith("layout_")) {
            return;
        }

        // Fundamental layout params valid for all ViewGroups
        if (attrName.equals("layout_width") || attrName.equals("layout_height") ||
                attrName.startsWith("layout_margin") || attrName.startsWith("layout_padding")) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        Node parentNode = owner.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        String parentTag = ((Element) parentNode).getTagName();
        if (!isValidForParent(attrName, parentTag)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Invalid layout param in a " + parentTag + ": " + attrName);
        }
    }

    private boolean isValidForParent(@NonNull String attrName, @NonNull String parentTag) {
        List<String> validParents = PARAM_PARENT_MAP.get(attrName);
        if (validParents != null) {
            return matchesParent(parentTag, validParents);
        }

        if (attrName.startsWith("layout_constraint")) {
            return parentTag.contains("ConstraintLayout");
        }
        if (attrName.startsWith("layout_flex")) {
            return parentTag.contains("FlexboxLayout");
        }

        // Unknown params are assumed valid to avoid false positives
        return true;
    }

    private boolean matchesParent(@NonNull String parentTag, @NonNull List<String> validParents) {
        for (String valid : validParents) {
            if (parentTag.equals(valid) || parentTag.endsWith("." + valid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Cleanup shared state if necessary
    }
}