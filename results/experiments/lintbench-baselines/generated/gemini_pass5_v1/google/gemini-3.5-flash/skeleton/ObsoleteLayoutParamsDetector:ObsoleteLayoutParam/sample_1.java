package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

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

    private static final java.util.Set<String> RELATIVE_LAYOUT_PARAMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "layout_alignParentLeft", "layout_alignParentTop", "layout_alignParentRight", "layout_alignParentBottom",
            "layout_alignLeft", "layout_alignTop", "layout_alignRight", "layout_alignBottom",
            "layout_alignParentStart", "layout_alignParentEnd", "layout_alignStart", "layout_alignEnd",
            "layout_alignBaseline", "layout_alignWithParentIfMissing",
            "layout_toLeftOf", "layout_toRightOf", "layout_toStartOf", "layout_toEndOf",
            "layout_above", "layout_below",
            "layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical"
    ));

    private static final java.util.Set<String> COORDINATOR_LAYOUT_PARAMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "layout_behavior", "layout_anchor", "layout_anchorGravity", "layout_dodgeInsetEdges", "layout_insetEdge"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singleton(XmlScanner.ALL);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handled in visitElement
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element parent = getParentElement(element);
        if (parent == null) {
            return;
        }
        String parentTag = parent.getTagName();
        if (parentTag.equals("merge")) {
            return;
        }

        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            org.w3c.dom.Node node = attributes.item(i);
            if (node instanceof Attr) {
                Attr attr = (Attr) node;
                String localName = attr.getLocalName();
                if (localName == null) {
                    localName = attr.getName();
                    int colon = localName.indexOf(':');
                    if (colon != -1) {
                        localName = localName.substring(colon + 1);
                    }
                }
                if (localName.startsWith("layout_")) {
                    checkLayoutParam(context, parentTag, attr, localName);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    private void checkLayoutParam(@NonNull XmlContext context, @NonNull String parentTag, @NonNull Attr attr, @NonNull String localName) {
        boolean isObsolete = false;
        String reason = "";

        if (RELATIVE_LAYOUT_PARAMS.contains(localName)) {
            if (!isRelativeLayout(parentTag)) {
                isObsolete = true;
                reason = String.format("`%s` is only supported by `RelativeLayout` targets", localName);
            }
        } else if (localName.equals("layout_weight")) {
            if (!isLinearLayout(parentTag)) {
                isObsolete = true;
                reason = "`layout_weight` is only supported by `LinearLayout` targets";
            }
        } else if (localName.equals("layout_gravity")) {
            if (!supportsGravity(parentTag)) {
                isObsolete = true;
                reason = String.format("`layout_gravity` is not supported by `%s`", parentTag);
            }
        } else if (localName.startsWith("layout_constraint")) {
            if (!isConstraintLayout(parentTag)) {
                isObsolete = true;
                reason = String.format("`%s` is only supported by `ConstraintLayout` targets", localName);
            }
        } else if (localName.equals("layout_column") || localName.equals("layout_row")
                || localName.equals("layout_columnSpan") || localName.equals("layout_rowSpan")
                || localName.equals("layout_columnWeight") || localName.equals("layout_rowWeight")) {
            if (localName.equals("layout_column")) {
                if (!isTableOrGridLayout(parentTag)) {
                    isObsolete = true;
                    reason = "`layout_column` is only supported by `GridLayout` or `TableRow` targets";
                }
            } else {
                if (!isGridLayout(parentTag)) {
                    isObsolete = true;
                    reason = String.format("`%s` is only supported by `GridLayout` targets", localName);
                }
            }
        } else if (localName.equals("layout_span")) {
            if (!isTableRow(parentTag)) {
                isObsolete = true;
                reason = "`layout_span` is only supported by `TableRow` targets";
            }
        } else if (COORDINATOR_LAYOUT_PARAMS.contains(localName)) {
            if (!isCoordinatorLayout(parentTag)) {
                isObsolete = true;
                reason = String.format("`%s` is only supported by `CoordinatorLayout` targets", localName);
            }
        }

        if (isObsolete) {
            context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    reason);
        }
    }

    private static Element getParentElement(Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        if (parent instanceof Element) {
            return (Element) parent;
        }
        return null;
    }

    private static boolean isRelativeLayout(String tag) {
        return tag.equals("RelativeLayout") || tag.endsWith(".RelativeLayout");
    }

    private static boolean isLinearLayout(String tag) {
        return tag.equals("LinearLayout") || tag.endsWith(".LinearLayout")
                || tag.equals("RadioGroup") || tag.endsWith(".RadioGroup")
                || tag.equals("TableLayout") || tag.endsWith(".TableLayout")
                || tag.equals("TableRow") || tag.endsWith(".TableRow");
    }

    private static boolean supportsGravity(String tag) {
        return !tag.equals("RelativeLayout") && !tag.endsWith(".RelativeLayout")
                && !tag.equals("ConstraintLayout") && !tag.endsWith(".ConstraintLayout");
    }

    private static boolean isConstraintLayout(String tag) {
        return tag.equals("ConstraintLayout") || tag.endsWith(".ConstraintLayout")
                || tag.equals("MotionLayout") || tag.endsWith(".MotionLayout");
    }

    private static boolean isGridLayout(String tag) {
        return tag.equals("GridLayout") || tag.endsWith(".GridLayout");
    }

    private static boolean isTableRow(String tag) {
        return tag.equals("TableRow") || tag.endsWith(".TableRow");
    }

    private static boolean isTableOrGridLayout(String tag) {
        return isGridLayout(tag) || isTableRow(tag);
    }

    private static boolean isCoordinatorLayout(String tag) {
        return tag.equals("CoordinatorLayout") || tag.endsWith(".CoordinatorLayout");
    }
}