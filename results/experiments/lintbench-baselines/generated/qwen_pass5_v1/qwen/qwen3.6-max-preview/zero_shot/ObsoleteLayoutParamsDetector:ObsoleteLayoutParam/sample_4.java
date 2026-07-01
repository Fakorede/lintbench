package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
        "ObsoleteLayoutParam",
        "Obsolete layout params",
        "The given layout_param is not defined for the given layout, meaning it has no effect. " +
        "This usually happens when you change the parent layout or move view code around without " +
        "updating the layout params. This will cause useless attribute processing at runtime, and " +
        "is misleading for others reading the layout so the parameter should be removed.",
        Category.CORRECTNESS, 6, Severity.WARNING,
        new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent == null) {
            return;
        }

        String parentTag = parent.getTagName();
        if (parentTag.equals(SdkConstants.TAG_MERGE)) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
            }

            if (name.startsWith("layout_") && !isAllowed(parentTag, name)) {
                String ns = attr.getNamespaceURI();
                if (ns == null || ns.equals(SdkConstants.ANDROID_URI) || ns.equals(SdkConstants.AUTO_URI)) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                        String.format("Invalid layout param '%1$s' in a '%2$s'", name, parentTag));
                }
            }
        }
    }

    private static boolean isAllowed(@NonNull String parentTag, @NonNull String attrName) {
        if (attrName.equals("layout_width") || attrName.equals("layout_height") || attrName.equals("layout_description")) {
            return true;
        }
        if (attrName.startsWith("layout_margin")) {
            return true;
        }

        String simpleTag = parentTag;
        int dot = simpleTag.lastIndexOf('.');
        if (dot != -1) {
            simpleTag = simpleTag.substring(dot + 1);
        }

        switch (simpleTag) {
            case "LinearLayout":
                return attrName.equals("layout_weight") || attrName.equals("layout_gravity");
            case "FrameLayout":
            case "DrawerLayout":
            case "Toolbar":
            case "ViewPager":
            case "ViewPager2":
                return attrName.equals("layout_gravity");
            case "RelativeLayout":
                return attrName.startsWith("layout_align") || attrName.startsWith("layout_to") ||
                       attrName.startsWith("layout_center") || attrName.equals("layout_above") ||
                       attrName.equals("layout_below") || attrName.equals("layout_alignWithParentIfMissing") ||
                       attrName.equals("layout_ignoreGravity");
            case "GridLayout":
                return attrName.equals("layout_row") || attrName.equals("layout_column") ||
                       attrName.equals("layout_rowSpan") || attrName.equals("layout_columnSpan") ||
                       attrName.equals("layout_rowWeight") || attrName.equals("layout_columnWeight") ||
                       attrName.equals("layout_gravity");
            case "ConstraintLayout":
            case "MotionLayout":
                return attrName.startsWith("layout_constraint") || attrName.startsWith("layout_goneMargin") ||
                       attrName.equals("layout_editor_absoluteX") || attrName.equals("layout_editor_absoluteY") ||
                       attrName.equals("layout_wrapBehaviorInParent");
            case "CoordinatorLayout":
                return attrName.equals("layout_gravity") || attrName.equals("layout_anchor") ||
                       attrName.equals("layout_anchorGravity") || attrName.equals("layout_behavior") ||
                       attrName.equals("layout_keyline") || attrName.equals("layout_insetEdge") ||
                       attrName.equals("layout_dodgeInsetEdges");
            case "AppBarLayout":
                return attrName.equals("layout_scrollFlags") || attrName.equals("layout_scrollInterpolator");
            case "CollapsingToolbarLayout":
                return attrName.equals("layout_collapseMode") || attrName.equals("layout_collapseParallaxMultiplier");
            case "TableRow":
                return attrName.equals("layout_span") || attrName.equals("layout_column");
            case "AbsoluteLayout":
                return attrName.equals("layout_x") || attrName.equals("layout_y");
            default:
                return true;
        }
    }
}