package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no "
                    + "effect. This usually happens when you change the parent layout or move view "
                    + "code around without updating the layout params. This will cause useless "
                    + "attribute processing at runtime, and is misleading for others reading the "
                    + "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String AUTO_URI = "http://schemas.android.com/apk/res-auto";

    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ATTR_LAYOUT_SPAN = "layout_span";
    private static final String ATTR_LAYOUT_COLUMN = "layout_column";
    private static final String ATTR_LAYOUT_ROW = "layout_row";
    private static final String ATTR_LAYOUT_COLUMN_SPAN = "layout_columnSpan";
    private static final String ATTR_LAYOUT_ROW_SPAN = "layout_rowSpan";
    private static final String ATTR_LAYOUT_COLUMN_WEIGHT = "layout_columnWeight";
    private static final String ATTR_LAYOUT_ROW_WEIGHT = "layout_rowWeight";

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        if (parentTag.equals("merge")) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getLocalName();
            if (name == null || !name.startsWith("layout_")) {
                continue;
            }
            String namespace = attribute.getNamespaceURI();
            if (namespace == null) {
                continue;
            }
            if (!namespace.equals(ANDROID_URI) && !namespace.equals(AUTO_URI)) {
                continue;
            }

            checkAttribute(context, attribute, name, parentTag);
        }
    }

    private void checkAttribute(XmlContext context, Attr attribute, String name, String parentTag) {
        if (name.equals(ATTR_LAYOUT_WIDTH) || name.equals(ATTR_LAYOUT_HEIGHT)) {
            return;
        }

        String parent = parentTag;
        int dot = parent.lastIndexOf('.');
        if (dot != -1) {
            parent = parent.substring(dot + 1);
        }

        if (!isKnownLayout(parent)) {
            return;
        }

        if (isRelativeLayoutParam(name)) {
            if (!isRelativeLayout(parent)) {
                String message = String.format(
                        "Invalid layout param: `%1$s` is only supported by children of "
                        + "`RelativeLayout` (parent is `%2$s`)", name, parent);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        } else if (name.equals(ATTR_LAYOUT_GRAVITY)) {
            if (isRelativeLayout(parent)) {
                String message = "Invalid layout param: `layout_gravity` is not supported by children of "
                        + "`RelativeLayout` (use `layout_centerInParent` etc.)";
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            } else if (isConstraintLayout(parent)) {
                String message = "Invalid layout param: `layout_gravity` is not supported by children of "
                        + "`ConstraintLayout` (use `layout_constraintXXX` etc.)";
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        } else if (name.equals(ATTR_LAYOUT_WEIGHT)) {
            if (!isLinearLayout(parent)) {
                String message = String.format(
                        "Invalid layout param: `layout_weight` is only supported by children of "
                        + "`LinearLayout` (parent is `%1$s`)", parent);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        } else if (isGridLayoutParam(name)) {
            if (!isGridLayout(parent)) {
                String message = String.format(
                        "Invalid layout param: `%1$s` is only supported by children of "
                        + "`GridLayout` (parent is `%2$s`)", name, parent);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        } else if (name.equals(ATTR_LAYOUT_SPAN)) {
            if (!isTableRow(parent)) {
                String message = String.format(
                        "Invalid layout param: `layout_span` is only supported by children of "
                        + "`TableRow` (parent is `%1$s`)", parent);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        } else if (isConstraintLayoutParam(name)) {
            if (!isConstraintLayout(parent)) {
                String message = String.format(
                        "Invalid layout param: `%1$s` is only supported by children of "
                        + "`ConstraintLayout` (parent is `%2$s`)", name, parent);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        }
    }

    private static boolean isRelativeLayoutParam(String name) {
        return name.startsWith("layout_align")
                || name.startsWith("layout_to")
                || name.startsWith("layout_above")
                || name.startsWith("layout_below")
                || name.startsWith("layout_center")
                || name.equals("layout_alignWithParentIfMissing");
    }

    private static boolean isGridLayoutParam(String name) {
        return name.equals(ATTR_LAYOUT_COLUMN)
                || name.equals(ATTR_LAYOUT_ROW)
                || name.equals(ATTR_LAYOUT_COLUMN_SPAN)
                || name.equals(ATTR_LAYOUT_ROW_SPAN)
                || name.equals(ATTR_LAYOUT_COLUMN_WEIGHT)
                || name.equals(ATTR_LAYOUT_ROW_WEIGHT);
    }

    private static boolean isConstraintLayoutParam(String name) {
        return name.startsWith("layout_constraint") || name.startsWith("layout_goneMargin");
    }

    private static boolean isRelativeLayout(String parent) {
        return parent.equals("RelativeLayout") || parent.equals("PercentRelativeLayout");
    }

    private static boolean isLinearLayout(String parent) {
        return parent.equals("LinearLayout")
                || parent.equals("TableLayout")
                || parent.equals("TableRow")
                || parent.equals("RadioGroup");
    }

    private static boolean isGridLayout(String parent) {
        return parent.equals("GridLayout") || parent.equals("TableRow");
    }

    private static boolean isTableRow(String parent) {
        return parent.equals("TableRow");
    }

    private static boolean isConstraintLayout(String parent) {
        return parent.equals("ConstraintLayout") || parent.equals("MotionLayout");
    }

    private static boolean isKnownLayout(String parent) {
        return parent.equals("LinearLayout")
                || parent.equals("RelativeLayout")
                || parent.equals("FrameLayout")
                || parent.equals("GridLayout")
                || parent.equals("TableLayout")
                || parent.equals("TableRow")
                || parent.equals("AbsoluteLayout")
                || parent.equals("ConstraintLayout")
                || parent.equals("MotionLayout")
                || parent.equals("CoordinatorLayout")
                || parent.equals("DrawerLayout")
                || parent.equals("ScrollView")
                || parent.equals("NestedScrollView")
                || parent.equals("ViewPager")
                || parent.equals("ViewPager2")
                || parent.equals("RecyclerView")
                || parent.equals("ListView")
                || parent.equals("GridView")
                || parent.equals("SwipeRefreshLayout")
                || parent.equals("RadioGroup")
                || parent.equals("PercentRelativeLayout");
    }
}