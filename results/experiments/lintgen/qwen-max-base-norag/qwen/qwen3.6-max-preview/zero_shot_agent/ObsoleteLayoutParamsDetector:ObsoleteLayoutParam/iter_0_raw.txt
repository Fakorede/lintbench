package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
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
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final Map<String, Set<String>> LAYOUT_PARAMS_MAP = new HashMap<>();

    static {
        LAYOUT_PARAMS_MAP.put("LinearLayout", new HashSet<>(Arrays.asList("layout_gravity", "layout_weight")));
        LAYOUT_PARAMS_MAP.put("TableLayout", new HashSet<>(Arrays.asList("layout_gravity", "layout_weight")));
        LAYOUT_PARAMS_MAP.put("FrameLayout", new HashSet<>(Arrays.asList("layout_gravity")));
        LAYOUT_PARAMS_MAP.put("RelativeLayout", new HashSet<>(Arrays.asList(
                "layout_above", "layout_alignBaseline", "layout_alignBottom", "layout_alignEnd",
                "layout_alignLeft", "layout_alignParentBottom", "layout_alignParentEnd",
                "layout_alignParentLeft", "layout_alignParentRight", "layout_alignParentStart",
                "layout_alignParentTop", "layout_alignRight", "layout_alignStart", "layout_alignTop",
                "layout_alignWithParentIfMissing", "layout_below", "layout_centerHorizontal",
                "layout_centerInParent", "layout_centerVertical", "layout_toEndOf", "layout_toLeftOf",
                "layout_toRightOf", "layout_toStartOf"
        )));
        LAYOUT_PARAMS_MAP.put("GridLayout", new HashSet<>(Arrays.asList(
                "layout_column", "layout_columnSpan", "layout_columnWeight", "layout_gravity",
                "layout_row", "layout_rowSpan", "layout_rowWeight"
        )));
        LAYOUT_PARAMS_MAP.put("CoordinatorLayout", new HashSet<>(Arrays.asList(
                "layout_anchor", "layout_anchorGravity", "layout_behavior", "layout_keyline",
                "layout_insetEdge", "layout_dodgeInsetEdges"
        )));
        LAYOUT_PARAMS_MAP.put("DrawerLayout", new HashSet<>(Arrays.asList("layout_gravity")));
        LAYOUT_PARAMS_MAP.put("TableRow", new HashSet<>(Arrays.asList("layout_column", "layout_span")));
        LAYOUT_PARAMS_MAP.put("AbsoluteLayout", new HashSet<>(Arrays.asList("layout_x", "layout_y")));
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        if (parentTag.equals("merge")) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String ns = attr.getNamespaceURI();
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
            }

            if (!SdkConstants.ANDROID_URI.equals(ns)) {
                continue;
            }
            if (!name.startsWith("layout_")) {
                continue;
            }
            if (name.equals("layout_width") || name.equals("layout_height")) {
                continue;
            }
            if (name.startsWith("layout_margin")) {
                continue;
            }

            if (!isValidParam(parentTag, name)) {
                String message = String.format(
                        "Invalid layout param in a `%1$s`: `%2$s`", parentTag, name);
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }

    private static boolean isValidParam(String parentTag, String paramName) {
        String simpleName = parentTag;
        int dot = simpleName.lastIndexOf('.');
        if (dot != -1) {
            simpleName = simpleName.substring(dot + 1);
        }

        if (simpleName.equals("ConstraintLayout")) {
            return paramName.startsWith("layout_constraint") ||
                   paramName.startsWith("layout_goneMargin") ||
                   paramName.startsWith("layout_editor_") ||
                   paramName.equals("layout_constrainedWidth") ||
                   paramName.equals("layout_constrainedHeight") ||
                   paramName.equals("layout_constraintTag");
        }

        Set<String> valid = LAYOUT_PARAMS_MAP.get(simpleName);
        if (valid == null) {
            return true; // Unknown parent layout, skip check to avoid false positives
        }
        return valid.contains(paramName);
    }
}