package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
    private static final String TOOLS_URI = "http://schemas.android.com/tools";

    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ATTR_LAYOUT_SPAN = "layout_span";
    private static final String ATTR_LAYOUT_COLUMN = "layout_column";
    private static final String ATTR_LAYOUT_ROW = "layout_row";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

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
            parentTag = parent.getAttributeNS(TOOLS_URI, "parentTag");
            if (parentTag == null || parentTag.isEmpty()) {
                parentTag = parent.getAttribute("tools:parentTag");
                if (parentTag == null || parentTag.isEmpty()) {
                    return;
                }
            }
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getLocalName();
            if (name == null || !name.startsWith("layout_")) {
                continue;
            }
            String namespace = attribute.getNamespaceURI();
            if (namespace == null || !namespace.equals(ANDROID_URI)) {
                continue;
            }

            if (name.equals(ATTR_LAYOUT_WIDTH) || name.equals(ATTR_LAYOUT_HEIGHT)) {
                continue;
            }

            String parentName = parentTag;
            int dot = parentName.lastIndexOf('.');
            if (dot != -1) {
                parentName = parentName.substring(dot + 1);
            }

            if (!isKnownLayout(parentName)) {
                continue;
            }

            if (isRelativeLayoutParam(name)) {
                if (!isRelativeLayout(parentName)) {
                    String message = String.format(
                            "Invalid layout param: `%1$s` is only supported by children of "
                            + "`RelativeLayout` (parent is `%2$s`)", name, parentTag);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (name.equals(ATTR_LAYOUT_GRAVITY)) {
                if (isRelativeLayout(parentName)) {
                    String message = "Invalid layout param: `layout_gravity` is not supported by "
                            + "children of `RelativeLayout` (use `layout_centerInParent` etc.)";
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (name.equals(ATTR_LAYOUT_WEIGHT)) {
                if (!isLinearLayout(parentName)) {
                    String message = String.format(
                            "Invalid layout param: `layout_weight` is only supported by children of "
                            + "`LinearLayout` (parent is `%1$s`)", parentTag);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (isGridLayoutParam(name)) {
                if (!isGridLayout(parentName)) {
                    String message = String.format(
                            "Invalid layout param: `%1$s` is only supported by children of "
                            + "`GridLayout` (parent is `%2$s`)", name, parentTag);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (name.equals(ATTR_LAYOUT_SPAN)) {
                if (!isTableRow(parentName)) {
                    String message = String.format(
                            "Invalid layout param: `layout_span` is only supported by children of "
                            + "`TableRow` (parent is `%1$s`)", parentTag);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (name.equals(ATTR_LAYOUT_COLUMN)) {
                if (!isTableRow(parentName) && !isGridLayout(parentName)) {
                    String message = String.format(
                            "Invalid layout param: `layout_column` is only supported by children of "
                            + "`GridLayout` or `TableRow` (parent is `%1$s`)", parentTag);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
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
        return name.equals(ATTR_LAYOUT_ROW)
                || name.equals("layout_rowSpan")
                || name.equals("layout_columnSpan")
                || name.equals("layout_rowWeight")
                || name.equals("layout_columnWeight");
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
        return parent.equals("GridLayout");
    }

    private static boolean isTableRow(String parent) {
        return parent.equals("TableRow");
    }

    private static boolean isKnownLayout(String parent) {
        return parent.equals("LinearLayout")
                || parent.equals("RelativeLayout")
                || parent.equals("FrameLayout")
                || parent.equals("GridLayout")
                || parent.equals("TableLayout")
                || parent.equals("TableRow")
                || parent.equals("AbsoluteLayout")
                || parent.equals("RadioGroup")
                || parent.equals("PercentRelativeLayout");
    }
}