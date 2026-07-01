package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
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

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        org.w3c.dom.Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        if (!isKnownParent(parentTag)) {
            return;
        }

        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String nodeName = attr.getNodeName();
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = nodeName;
                int colon = localName.indexOf(':');
                if (colon != -1) {
                    localName = localName.substring(colon + 1);
                }
            }

            if (localName.startsWith("layout_")) {
                if (!isAttributeAllowed(parentTag, localName)) {
                    String message = String.format(
                            "The `layout_` attribute `%s` is obsolete/ignored because the parent layout is `%s`",
                            localName, getSimpleName(parentTag));
                    context.report(ISSUE, attr, context.getLocation(attr), message);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
    }

    private static String getSimpleName(String tagName) {
        int lastDot = tagName.lastIndexOf('.');
        if (lastDot != -1) {
            return tagName.substring(lastDot + 1);
        }
        return tagName;
    }

    private static boolean isKnownParent(String parentTag) {
        String simpleName = getSimpleName(parentTag);
        switch (simpleName) {
            case "LinearLayout":
            case "RelativeLayout":
            case "FrameLayout":
            case "GridLayout":
            case "TableRow":
            case "TableLayout":
            case "AbsoluteLayout":
            case "ConstraintLayout":
            case "CoordinatorLayout":
            case "DrawerLayout":
            case "ScrollView":
            case "HorizontalScrollView":
            case "NestedScrollView":
            case "ViewPager":
            case "ViewPager2":
                return true;
            default:
                return false;
        }
    }

    private static boolean isAttributeAllowed(String parentTag, String attrName) {
        if (!attrName.startsWith("layout_")) {
            return true;
        }

        if (attrName.equals("layout_width") || attrName.equals("layout_height") ||
            attrName.equals("layout_margin") || attrName.equals("layout_marginLeft") ||
            attrName.equals("layout_marginTop") || attrName.equals("layout_marginRight") ||
            attrName.equals("layout_marginBottom") || attrName.equals("layout_marginStart") ||
            attrName.equals("layout_marginEnd") || attrName.equals("layout_marginHorizontal") ||
            attrName.equals("layout_marginVertical")) {
            return true;
        }

        String simpleName = getSimpleName(parentTag);
        switch (simpleName) {
            case "LinearLayout":
            case "TableLayout":
                return attrName.equals("layout_weight") || attrName.equals("layout_gravity");

            case "RelativeLayout":
                return attrName.equals("layout_above") ||
                       attrName.equals("layout_below") ||
                       attrName.equals("layout_toLeftOf") ||
                       attrName.equals("layout_toRightOf") ||
                       attrName.equals("layout_toStartOf") ||
                       attrName.equals("layout_toEndOf") ||
                       attrName.equals("layout_alignBaseline") ||
                       attrName.equals("layout_alignBottom") ||
                       attrName.equals("layout_alignLeft") ||
                       attrName.equals("layout_alignRight") ||
                       attrName.equals("layout_alignTop") ||
                       attrName.equals("layout_alignStart") ||
                       attrName.equals("layout_alignEnd") ||
                       attrName.equals("layout_alignParentBottom") ||
                       attrName.equals("layout_alignParentLeft") ||
                       attrName.equals("layout_alignParentRight") ||
                       attrName.equals("layout_alignParentTop") ||
                       attrName.equals("layout_alignParentStart") ||
                       attrName.equals("layout_alignParentEnd") ||
                       attrName.equals("layout_centerHorizontal") ||
                       attrName.equals("layout_centerInParent") ||
                       attrName.equals("layout_centerVertical");

            case "FrameLayout":
            case "ScrollView":
            case "HorizontalScrollView":
            case "NestedScrollView":
            case "DrawerLayout":
            case "ViewPager":
            case "ViewPager2":
                return attrName.equals("layout_gravity");

            case "GridLayout":
                return attrName.equals("layout_column") ||
                       attrName.equals("layout_columnSpan") ||
                       attrName.equals("layout_columnWeight") ||
                       attrName.equals("layout_row") ||
                       attrName.equals("layout_rowSpan") ||
                       attrName.equals("layout_rowWeight") ||
                       attrName.equals("layout_gravity");

            case "TableRow":
                return attrName.equals("layout_column") ||
                       attrName.equals("layout_span");

            case "AbsoluteLayout":
                return attrName.equals("layout_x") ||
                       attrName.equals("layout_y");

            case "ConstraintLayout":
                return attrName.startsWith("layout_constraint") ||
                       attrName.startsWith("layout_gone") ||
                       attrName.startsWith("layout_editor_") ||
                       attrName.equals("layout_wrapBehaviorInParent");

            case "CoordinatorLayout":
                return attrName.equals("layout_behavior") ||
                       attrName.equals("layout_anchor") ||
                       attrName.equals("layout_anchorGravity") ||
                       attrName.equals("layout_keyline") ||
                       attrName.equals("layout_insetEdge") ||
                       attrName.equals("layout_dodgeInsetEdges") ||
                       attrName.equals("layout_gravity");

            default:
                return true;
        }
    }
}