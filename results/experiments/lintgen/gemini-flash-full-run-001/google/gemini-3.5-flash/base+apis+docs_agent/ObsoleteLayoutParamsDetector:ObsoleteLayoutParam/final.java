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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;

public class ObsoleteLayoutParamsDetector extends Detector implements XmlScanner {

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
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String namespace = attr.getNamespaceURI();
            if (!"http://schemas.android.com/apk/res/android".equals(namespace)) {
                continue;
            }
            String localName = attr.getLocalName();
            if (localName == null || !localName.startsWith("layout_")) {
                continue;
            }

            String badParent = getInvalidParent(localName, parentTag);
            if (badParent != null) {
                String message = String.format(
                        "Layout parameter `%s` is ignored in a `%s` parent",
                        localName,
                        badParent
                );
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }

    private static boolean isLayout(String tag, String suffix) {
        return tag.equals(suffix) || tag.endsWith("." + suffix);
    }

    private static boolean isLinearLayout(String tag) {
        return isLayout(tag, "LinearLayout")
                || isLayout(tag, "LinearLayoutCompat")
                || isLayout(tag, "RadioGroup");
    }

    private static boolean isRelativeLayout(String tag) {
        return isLayout(tag, "RelativeLayout");
    }

    private static boolean isFrameLayout(String tag) {
        return isLayout(tag, "FrameLayout")
                || isLayout(tag, "ScrollView")
                || isLayout(tag, "NestedScrollView");
    }

    private static boolean isGridLayout(String tag) {
        return isLayout(tag, "GridLayout");
    }

    private static boolean isConstraintLayout(String tag) {
        return isLayout(tag, "ConstraintLayout");
    }

    private static boolean isTableRow(String tag) {
        return isLayout(tag, "TableRow");
    }

    private static boolean isRelativeParam(String name) {
        switch (name) {
            case "layout_alignParentLeft":
            case "layout_alignParentTop":
            case "layout_alignParentRight":
            case "layout_alignParentBottom":
            case "layout_alignParentStart":
            case "layout_alignParentEnd":
            case "layout_centerInParent":
            case "layout_centerHorizontal":
            case "layout_centerVertical":
            case "layout_toLeftOf":
            case "layout_toRightOf":
            case "layout_above":
            case "layout_below":
            case "layout_toStartOf":
            case "layout_toEndOf":
            case "layout_alignLeft":
            case "layout_alignTop":
            case "layout_alignRight":
            case "layout_alignBottom":
            case "layout_alignStart":
            case "layout_alignEnd":
            case "layout_alignBaseline":
            case "layout_alignWithParentIfMissing":
                return true;
            default:
                return false;
        }
    }

    private static boolean isLinearParam(String name) {
        return "layout_weight".equals(name);
    }

    private static boolean isGridParam(String name) {
        switch (name) {
            case "layout_row":
            case "layout_rowSpan":
            case "layout_rowWeight":
            case "layout_column":
            case "layout_columnSpan":
            case "layout_columnWeight":
                return true;
            default:
                return false;
        }
    }

    private static boolean isTableRowParam(String name) {
        return "layout_span".equals(name);
    }

    private static String getInvalidParent(String localName, String parentTag) {
        if (isLinearLayout(parentTag)) {
            if (isRelativeParam(localName)) {
                return "LinearLayout";
            }
            if (isGridParam(localName)) {
                return "LinearLayout";
            }
            if (isTableRowParam(localName)) {
                return "LinearLayout";
            }
        } else if (isRelativeLayout(parentTag)) {
            if (isLinearParam(localName)) {
                return "RelativeLayout";
            }
            if (isGridParam(localName)) {
                return "RelativeLayout";
            }
            if (isTableRowParam(localName)) {
                return "RelativeLayout";
            }
            if ("layout_gravity".equals(localName)) {
                return "RelativeLayout";
            }
        } else if (isFrameLayout(parentTag)) {
            if (isLinearParam(localName)) {
                return "FrameLayout";
            }
            if (isRelativeParam(localName)) {
                return "FrameLayout";
            }
            if (isGridParam(localName)) {
                return "FrameLayout";
            }
            if (isTableRowParam(localName)) {
                return "FrameLayout";
            }
        } else if (isGridLayout(parentTag)) {
            if (isLinearParam(localName)) {
                return "GridLayout";
            }
            if (isRelativeParam(localName)) {
                return "GridLayout";
            }
            if (isTableRowParam(localName)) {
                return "GridLayout";
            }
        } else if (isConstraintLayout(parentTag)) {
            if (isLinearParam(localName)) {
                return "ConstraintLayout";
            }
            if (isRelativeParam(localName)) {
                return "ConstraintLayout";
            }
            if (isGridParam(localName)) {
                return "ConstraintLayout";
            }
            if (isTableRowParam(localName)) {
                return "ConstraintLayout";
            }
            if ("layout_gravity".equals(localName)) {
                return "ConstraintLayout";
            }
        } else if (isTableRow(parentTag)) {
            if (isRelativeParam(localName)) {
                return "TableRow";
            }
            if (isGridParam(localName) && !"layout_column".equals(localName)) {
                return "TableRow";
            }
        }
        return null;
    }
}