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
import org.w3c.dom.Node;

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
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ATTR_LAYOUT_ROW = "layout_row";
    private static final String ATTR_LAYOUT_COLUMN = "layout_column";
    private static final String ATTR_LAYOUT_SPAN = "layout_span";
    private static final String TABLE_ROW = "TableRow";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
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

        String tag = parentTag;
        int lastDot = tag.lastIndexOf('.');
        if (lastDot != -1) {
            tag = tag.substring(lastDot + 1);
        }

        boolean isObsolete = false;

        if (tag.equals("LinearLayout")) {
            if (isRelativeLayoutParam(name)) {
                isObsolete = true;
            } else if (isGridLayoutParam(name)) {
                isObsolete = true;
            } else if (isTableRowParam(name)) {
                isObsolete = true;
            }
        } else if (tag.equals("RelativeLayout")) {
            if (name.equals(ATTR_LAYOUT_GRAVITY)) {
                isObsolete = true;
            } else if (name.equals(ATTR_LAYOUT_WEIGHT)) {
                isObsolete = true;
            } else if (isGridLayoutParam(name)) {
                isObsolete = true;
            } else if (isTableRowParam(name)) {
                isObsolete = true;
            }
        } else if (tag.equals("FrameLayout")
                || tag.equals("ScrollView")
                || tag.equals("HorizontalScrollView")
                || tag.equals("NestedScrollView")) {
            if (name.equals(ATTR_LAYOUT_WEIGHT)) {
                isObsolete = true;
            } else if (isRelativeLayoutParam(name)) {
                isObsolete = true;
            } else if (isGridLayoutParam(name)) {
                isObsolete = true;
            } else if (isTableRowParam(name)) {
                isObsolete = true;
            }
        } else if (tag.equals("ConstraintLayout")) {
            if (name.equals(ATTR_LAYOUT_GRAVITY)) {
                isObsolete = true;
            } else if (name.equals(ATTR_LAYOUT_WEIGHT)) {
                isObsolete = true;
            } else if (isRelativeLayoutParam(name)) {
                isObsolete = true;
            } else if (isGridLayoutParam(name)) {
                isObsolete = true;
            } else if (isTableRowParam(name)) {
                isObsolete = true;
            }
        } else if (tag.equals("CoordinatorLayout")) {
            if (name.equals(ATTR_LAYOUT_WEIGHT)) {
                isObsolete = true;
            } else if (isRelativeLayoutParam(name)) {
                isObsolete = true;
            } else if (isGridLayoutParam(name)) {
                isObsolete = true;
            } else if (isTableRowParam(name)) {
                isObsolete = true;
            }
        } else if (tag.equals(TABLE_ROW)) {
            if (isRelativeLayoutParam(name)) {
                isObsolete = true;
            } else if (name.equals(ATTR_LAYOUT_ROW)
                    || name.equals("layout_rowSpan")
                    || name.equals("layout_columnSpan")
                    || name.equals("layout_rowWeight")
                    || name.equals("layout_columnWeight")) {
                isObsolete = true;
            }
        }

        if (isObsolete) {
            String message = String.format(
                    "Invalid layout param in a %1$s: %2$s",
                    parentTag, name);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private static boolean isRelativeLayoutParam(String name) {
        return name.startsWith("layout_align")
                || name.startsWith("layout_to")
                || name.startsWith("layout_above")
                || name.startsWith("layout_below")
                || name.startsWith("layout_center");
    }

    private static boolean isGridLayoutParam(String name) {
        return name.equals(ATTR_LAYOUT_ROW)
                || name.equals("layout_rowSpan")
                || name.equals(ATTR_LAYOUT_COLUMN)
                || name.equals("layout_columnSpan")
                || name.equals("layout_rowWeight")
                || name.equals("layout_columnWeight");
    }

    private static boolean isTableRowParam(String name) {
        return name.equals(ATTR_LAYOUT_COLUMN)
                || name.equals(ATTR_LAYOUT_SPAN);
    }
}