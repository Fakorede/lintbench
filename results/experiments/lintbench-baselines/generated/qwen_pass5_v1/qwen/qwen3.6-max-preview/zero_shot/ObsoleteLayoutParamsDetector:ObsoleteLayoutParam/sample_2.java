package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.*;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {
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

    private static final Map<String, Set<String>> ALLOWED_PARAMS = new HashMap<>();

    static {
        Set<String> linear = new HashSet<>(Arrays.asList("layout_weight", "layout_gravity"));
        ALLOWED_PARAMS.put("android.widget.LinearLayout", linear);
        ALLOWED_PARAMS.put("android.widget.RadioGroup", linear);

        Set<String> frame = new HashSet<>(Collections.singletonList("layout_gravity"));
        ALLOWED_PARAMS.put("android.widget.FrameLayout", frame);
        ALLOWED_PARAMS.put("android.widget.ViewFlipper", frame);
        ALLOWED_PARAMS.put("android.widget.ViewAnimator", frame);
        ALLOWED_PARAMS.put("android.widget.StackView", frame);
        ALLOWED_PARAMS.put("android.widget.AdapterViewFlipper", frame);
        ALLOWED_PARAMS.put("androidx.drawerlayout.widget.DrawerLayout", frame);
        ALLOWED_PARAMS.put("androidx.appcompat.widget.Toolbar", frame);
        ALLOWED_PARAMS.put("com.google.android.material.appbar.AppBarLayout", new HashSet<>(Arrays.asList("layout_scrollFlags", "layout_scrollEffect")));
        ALLOWED_PARAMS.put("com.google.android.material.appbar.CollapsingToolbarLayout", new HashSet<>(Arrays.asList("layout_collapseMode", "layout_collapseParallaxMultiplier")));

        Set<String> relative = new HashSet<>(Arrays.asList(
            "layout_above", "layout_alignBaseline", "layout_alignBottom", "layout_alignEnd",
            "layout_alignLeft", "layout_alignParentBottom", "layout_alignParentEnd",
            "layout_alignParentLeft", "layout_alignParentRight", "layout_alignParentStart",
            "layout_alignParentTop", "layout_alignRight", "layout_alignStart", "layout_alignTop",
            "layout_alignWithParentIfMissing", "layout_below", "layout_centerHorizontal",
            "layout_centerInParent", "layout_centerVertical", "layout_toEndOf", "layout_toLeftOf",
            "layout_toRightOf", "layout_toStartOf"
        ));
        ALLOWED_PARAMS.put("android.widget.RelativeLayout", relative);

        Set<String> grid = new HashSet<>(Arrays.asList(
            "layout_row", "layout_rowSpan", "layout_rowWeight", "layout_column",
            "layout_columnSpan", "layout_columnWeight", "layout_gravity"
        ));
        ALLOWED_PARAMS.put("android.widget.GridLayout", grid);
        ALLOWED_PARAMS.put("androidx.gridlayout.widget.GridLayout", grid);

        ALLOWED_PARAMS.put("android.widget.TableRow", new HashSet<>(Arrays.asList("layout_column", "layout_span")));

        ALLOWED_PARAMS.put("androidx.coordinatorlayout.widget.CoordinatorLayout", new HashSet<>(Arrays.asList(
            "layout_gravity", "layout_anchor", "layout_anchorGravity", "layout_behavior",
            "layout_dodgeInsetEdges", "layout_insetEdge", "layout_keyline"
        )));

        Set<String> constraint = new HashSet<>(Arrays.asList(
            "layout_constraintHorizontal_bias", "layout_constraintVertical_bias",
            "layout_constraintDimensionRatio", "layout_constraintCircle",
            "layout_constraintCircleRadius", "layout_constraintCircleAngle",
            "layout_editor_absoluteX", "layout_editor_absoluteY", "layout_wrapBehaviorInParent"
        ));
        ALLOWED_PARAMS.put("androidx.constraintlayout.widget.ConstraintLayout", constraint);
        ALLOWED_PARAMS.put("androidx.constraintlayout.motion.widget.MotionLayout", constraint);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent == null || parent.getParentNode() == null) {
            return;
        }

        String parentClass = context.resolveViewClass(parent);
        if (parentClass == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null || !name.startsWith("layout_")) {
                continue;
            }
            if (name.equals("layout_width") || name.equals("layout_height")) {
                continue;
            }
            if (name.startsWith("layout_margin")) {
                continue;
            }

            String ns = attr.getNamespaceURI();
            if (SdkConstants.TOOLS_URI.equals(ns)) {
                continue;
            }
            if (ns != null && !ns.isEmpty() && !SdkConstants.ANDROID_URI.equals(ns)) {
                continue;
            }

            if (!isParamValid(context, parentClass, name)) {
                String tag = context.resolveViewTag(parent);
                if (tag == null) {
                    tag = parent.getTagName();
                }
                String message = String.format("Invalid layout param in a `%1$s`: `%2$s`", tag, name);
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }

    private boolean isParamValid(XmlContext context, String parentClass, String attrName) {
        Set<String> allowed = ALLOWED_PARAMS.get(parentClass);
        if (allowed != null) {
            return checkAllowed(context, parentClass, attrName, allowed);
        }

        for (Map.Entry<String, Set<String>> entry : ALLOWED_PARAMS.entrySet()) {
            if (context.extendsClass(parentClass, entry.getKey(), false)) {
                return checkAllowed(context, parentClass, attrName, entry.getValue());
            }
        }

        return true;
    }

    private boolean checkAllowed(XmlContext context, String parentClass, String attrName, Set<String> allowed) {
        if (allowed.contains(attrName)) {
            return true;
        }
        if (context.extendsClass(parentClass, "androidx.constraintlayout.widget.ConstraintLayout", false)) {
            if (attrName.startsWith("layout_constraint") || attrName.startsWith("layout_goneMargin") || attrName.startsWith("layout_editor_")) {
                return true;
            }
        }
        return false;
    }
}