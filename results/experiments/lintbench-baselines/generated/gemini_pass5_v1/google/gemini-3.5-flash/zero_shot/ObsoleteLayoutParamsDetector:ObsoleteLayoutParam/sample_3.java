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
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has " +
            "no effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

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
        if ("merge".equals(parentTag)) {
            if (parent.hasAttributeNS("http://schemas.android.com/apk/res-auto", "parentTag")) {
                parentTag = parent.getAttributeNS("http://schemas.android.com/apk/res-auto", "parentTag");
            } else if (parent.hasAttribute("parentTag")) {
                parentTag = parent.getAttribute("parentTag");
            } else {
                return;
            }
        }

        ParentType parentType = getParentType(parentTag);
        if (parentType == ParentType.UNKNOWN) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String localName = attribute.getLocalName();
            if (localName == null || !localName.startsWith("layout_")) {
                continue;
            }

            if ("layout_width".equals(localName) || 
                "layout_height".equals(localName) || 
                localName.startsWith("layout_margin")) {
                continue;
            }

            if (!isParamValid(localName, parentType)) {
                String message = String.format(
                        "Invalid layout param `%s` for parent `%s`",
                        attribute.getName(), parentTag);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        }
    }

    private enum ParentType {
        LINEAR_LAYOUT,
        RELATIVE_LAYOUT,
        FRAME_LAYOUT,
        CONSTRAINT_LAYOUT,
        GRID_LAYOUT,
        TABLE_ROW,
        TABLE_LAYOUT,
        COORDINATOR_LAYOUT,
        DRAWER_LAYOUT,
        UNKNOWN
    }

    private ParentType getParentType(String tag) {
        if (tag == null) return ParentType.UNKNOWN;
        if (tag.endsWith("LinearLayout")) return ParentType.LINEAR_LAYOUT;
        if (tag.endsWith("RelativeLayout")) return ParentType.RELATIVE_LAYOUT;
        if (tag.endsWith("FrameLayout") || 
            tag.endsWith("ScrollView") || 
            tag.endsWith("NestedScrollView") || 
            tag.endsWith("CardView") || 
            tag.endsWith("ViewFlipper") || 
            tag.endsWith("ViewAnimator")) {
            return ParentType.FRAME_LAYOUT;
        }
        if (tag.endsWith("ConstraintLayout")) return ParentType.CONSTRAINT_LAYOUT;
        if (tag.endsWith("GridLayout")) return ParentType.GRID_LAYOUT;
        if (tag.endsWith("TableRow")) return ParentType.TABLE_ROW;
        if (tag.endsWith("TableLayout")) return ParentType.TABLE_LAYOUT;
        if (tag.endsWith("CoordinatorLayout")) return ParentType.COORDINATOR_LAYOUT;
        if (tag.endsWith("DrawerLayout")) return ParentType.DRAWER_LAYOUT;
        return ParentType.UNKNOWN;
    }

    private boolean isParamValid(String param, ParentType parentType) {
        if ("layout_weight".equals(param)) {
            return parentType == ParentType.LINEAR_LAYOUT || parentType == ParentType.TABLE_ROW || parentType == ParentType.TABLE_LAYOUT;
        }

        if ("layout_gravity".equals(param)) {
            return parentType == ParentType.LINEAR_LAYOUT || 
                   parentType == ParentType.FRAME_LAYOUT || 
                   parentType == ParentType.GRID_LAYOUT || 
                   parentType == ParentType.COORDINATOR_LAYOUT || 
                   parentType == ParentType.DRAWER_LAYOUT;
        }

        if (isRelativeLayoutParam(param)) {
            return parentType == ParentType.RELATIVE_LAYOUT;
        }

        if (param.startsWith("layout_constraint") || param.startsWith("layout_goneMargin")) {
            return parentType == ParentType.CONSTRAINT_LAYOUT;
        }

        if (isGridLayoutParam(param)) {
            return parentType == ParentType.GRID_LAYOUT || parentType == ParentType.TABLE_ROW;
        }

        if ("layout_behavior".equals(param) || "layout_anchor".equals(param) || "layout_anchorGravity".equals(param)) {
            return parentType == ParentType.COORDINATOR_LAYOUT;
        }

        return true;
    }

    private boolean isRelativeLayoutParam(String param) {
        switch (param) {
            case "layout_above":
            case "layout_below":
            case "layout_toLeftOf":
            case "layout_toRightOf":
            case "layout_toStartOf":
            case "layout_toEndOf":
            case "layout_alignLeft":
            case "layout_alignRight":
            case "layout_alignTop":
            case "layout_alignBottom":
            case "layout_alignBaseline":
            case "layout_alignStart":
            case "layout_alignEnd":
            case "layout_alignParentLeft":
            case "layout_alignParentRight":
            case "layout_alignParentTop":
            case "layout_alignParentBottom":
            case "layout_alignParentStart":
            case "layout_alignParentEnd":
            case "layout_centerHorizontal":
            case "layout_centerVertical":
            case "layout_centerInParent":
                return true;
            default:
                return false;
        }
    }

    private boolean isGridLayoutParam(String param) {
        switch (param) {
            case "layout_row":
            case "layout_column":
            case "layout_rowSpan":
            case "layout_columnSpan":
            case "layout_rowWeight":
            case "layout_columnWeight":
            case "layout_span":
                return true;
            default:
                return false;
        }
    }
}