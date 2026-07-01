package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Set<String> STANDARD_PARENTS = new HashSet<>(Arrays.asList(
            "LinearLayout", "RelativeLayout", "FrameLayout", "GridLayout", "TableLayout", "TableRow",
            "AbsoluteLayout", "CoordinatorLayout", "ConstraintLayout", "MotionLayout", "DrawerLayout",
            "ScrollView", "HorizontalScrollView"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singleton("*");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        if (parentTag.equals("merge")) {
            String parentAttr = parent.getAttributeNS("http://schemas.android.com/tools", "parentTag");
            if (parentAttr != null && !parentAttr.isEmpty()) {
                parentTag = parentAttr;
            } else {
                return;
            }
        }

        String shortParent = getShortTagName(parentTag);
        if (!STANDARD_PARENTS.contains(shortParent)) {
            return;
        }

        if (!isAllowed(name, shortParent)) {
            String message = String.format("The `layout_param` `%s` is not defined for the layout `%s` and has no effect", attribute.getName(), parentTag);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    private static String getShortTagName(String tagName) {
        int index = tagName.lastIndexOf('.');
        if (index != -1) {
            return tagName.substring(index + 1);
        }
        return tagName;
    }

    private static boolean isAllowed(String attrName, String parentLayout) {
        if (attrName.equals("layout_width") ||
                attrName.equals("layout_height") ||
                attrName.startsWith("layout_margin")) {
            return true;
        }

        if (attrName.equals("layout_gravity")) {
            return parentLayout.equals("FrameLayout") ||
                    parentLayout.equals("LinearLayout") ||
                    parentLayout.equals("GridLayout") ||
                    parentLayout.equals("TableRow") ||
                    parentLayout.equals("TableLayout") ||
                    parentLayout.equals("CoordinatorLayout") ||
                    parentLayout.equals("DrawerLayout") ||
                    parentLayout.equals("ScrollView") ||
                    parentLayout.equals("HorizontalScrollView");
        }

        if (attrName.equals("layout_weight")) {
            return parentLayout.equals("LinearLayout") ||
                    parentLayout.equals("TableRow") ||
                    parentLayout.equals("TableLayout");
        }

        if (attrName.startsWith("layout_constraint")) {
            return parentLayout.equals("ConstraintLayout") ||
                    parentLayout.equals("MotionLayout");
        }

        if (attrName.startsWith("layout_goneMargin")) {
            return parentLayout.equals("ConstraintLayout") ||
                    parentLayout.equals("MotionLayout");
        }

        if (attrName.startsWith("layout_constrained")) {
            return parentLayout.equals("ConstraintLayout") ||
                    parentLayout.equals("MotionLayout");
        }

        if (attrName.startsWith("layout_alignParent") ||
                attrName.startsWith("layout_center") ||
                attrName.startsWith("layout_to") ||
                attrName.startsWith("layout_align") ||
                attrName.equals("layout_above") ||
                attrName.equals("layout_below")) {
            return parentLayout.equals("RelativeLayout");
        }

        if (attrName.equals("layout_column")) {
            return parentLayout.equals("GridLayout") || parentLayout.equals("TableRow");
        }

        if (attrName.equals("layout_span")) {
            return parentLayout.equals("TableRow");
        }

        if (attrName.equals("layout_columnSpan") ||
                attrName.equals("layout_row") ||
                attrName.equals("layout_rowSpan") ||
                attrName.equals("layout_columnWeight") ||
                attrName.equals("layout_rowWeight")) {
            return parentLayout.equals("GridLayout");
        }

        if (attrName.equals("layout_x") || attrName.equals("layout_y")) {
            return parentLayout.equals("AbsoluteLayout");
        }

        if (attrName.equals("layout_behavior") ||
                attrName.equals("layout_anchor") ||
                attrName.equals("layout_anchorGravity") ||
                attrName.equals("layout_insetEdge") ||
                attrName.equals("layout_dodgeInsetEdges") ||
                attrName.equals("layout_keyline")) {
            return parentLayout.equals("CoordinatorLayout");
        }

        return true;
    }
}