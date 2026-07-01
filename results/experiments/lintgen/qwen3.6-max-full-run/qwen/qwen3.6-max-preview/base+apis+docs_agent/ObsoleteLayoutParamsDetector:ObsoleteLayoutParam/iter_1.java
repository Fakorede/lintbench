package com.android.tools.lint.checks;

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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collection;

public class ObsoleteLayoutParamsDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "ObsoleteLayoutParam",
        "Obsolete layout params",
        "The given layout_param is not defined for the given layout, meaning it has no effect. " +
        "This usually happens when you change the parent layout or move view code around without " +
        "updating the layout params. This will cause useless attribute processing at runtime, and " +
        "is misleading for others reading the layout so the parameter should be removed.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (tag.equals("merge") || tag.equals("requestFocus") || tag.equals("tag")) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        int dot = parentTag.lastIndexOf('.');
        if (dot != -1) {
            parentTag = parentTag.substring(dot + 1);
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
            }
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }

            if (!name.startsWith("layout_")) {
                continue;
            }

            if (!isAllowed(name, parentTag)) {
                String message = String.format("Invalid layout param in a `%s`: `%s`", parentTag, attr.getNodeName());
                context.report(ISSUE, context.getLocation(attr), message);
            }
        }
    }

    private static boolean isAllowed(String attrName, String parentTag) {
        if (attrName.equals("layout_width") || attrName.equals("layout_height")) {
            return true;
        }
        if (attrName.startsWith("layout_margin")) {
            return true;
        }

        if (attrName.equals("layout_weight")) {
            return isLinear(parentTag);
        }
        if (attrName.equals("layout_gravity")) {
            return isLinear(parentTag) || isFrame(parentTag) || parentTag.equals("GridLayout") ||
                   parentTag.equals("CoordinatorLayout") || parentTag.equals("DrawerLayout") ||
                   parentTag.equals("Toolbar") || parentTag.equals("AppBarLayout") ||
                   parentTag.equals("CollapsingToolbarLayout") || parentTag.equals("ViewPager") ||
                   parentTag.equals("ViewPager2");
        }
        if (attrName.startsWith("layout_align") || attrName.startsWith("layout_center") ||
            attrName.startsWith("layout_to") || attrName.equals("layout_above") ||
            attrName.equals("layout_below") || attrName.equals("layout_alignWithParentIfMissing")) {
            return parentTag.equals("RelativeLayout");
        }
        if (attrName.startsWith("layout_constraint") || attrName.startsWith("layout_goneMargin") ||
            attrName.startsWith("layout_editor_absolute") || attrName.equals("layout_wrapBehaviorInParent")) {
            return parentTag.equals("ConstraintLayout") || parentTag.equals("MotionLayout");
        }
        if (attrName.startsWith("layout_row") || attrName.startsWith("layout_column")) {
            return parentTag.equals("GridLayout");
        }
        if (attrName.equals("layout_anchor") || attrName.equals("layout_anchorGravity") ||
            attrName.equals("layout_behavior") || attrName.equals("layout_keyline") ||
            attrName.equals("layout_insetEdge") || attrName.equals("layout_dodgeInsetEdges")) {
            return parentTag.equals("CoordinatorLayout");
        }
        if (attrName.equals("layout_x") || attrName.equals("layout_y")) {
            return parentTag.equals("AbsoluteLayout");
        }

        return true;
    }

    private static boolean isLinear(String tag) {
        return tag.equals("LinearLayout") || tag.equals("TableRow") || tag.equals("RadioGroup");
    }

    private static boolean isFrame(String tag) {
        return tag.equals("FrameLayout") || tag.equals("FragmentContainerView");
    }
}