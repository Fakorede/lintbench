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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ObsoleteLayoutParamsDetector extends Detector implements XmlScanner {

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

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = getLocalName(parent.getTagName());

        if ("merge".equals(parentTag) || "include".equals(parentTag) || "ViewStub".equals(parentTag)) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String attrName = getLocalName(attr.getName());

            if (!attrName.startsWith("layout_")) {
                continue;
            }
            if (attrName.equals("layout_width") || attrName.equals("layout_height")) {
                continue;
            }
            if (attrName.startsWith("layout_margin") && !attrName.equals("layout_marginPercent")) {
                continue;
            }

            List<String> applicableParents = getApplicableParents(attrName);
            if (applicableParents == null) {
                continue;
            }

            boolean valid = false;
            for (String p : applicableParents) {
                if (parentTag.equals(p)) {
                    valid = true;
                    break;
                }
            }

            if (!valid) {
                String message = String.format("Invalid layout param in a `%s`: `%s`", parentTag, attrName);
                context.report(ISSUE, context.getLocation(attr), message);
            }
        }
    }

    private static List<String> getApplicableParents(String attrName) {
        if (attrName.equals("layout_weight")) {
            return Arrays.asList("LinearLayout", "TableRow", "TableLayout");
        }
        if (attrName.equals("layout_gravity")) {
            return Arrays.asList("FrameLayout", "LinearLayout", "GridLayout", "DrawerLayout", "Toolbar", "TableLayout", "ScrollView", "HorizontalScrollView", "NestedScrollView", "TableRow");
        }
        if (attrName.equals("layout_column") || attrName.equals("layout_span")) {
            return Arrays.asList("TableLayout", "TableRow", "GridLayout");
        }
        if (attrName.equals("layout_row") || attrName.startsWith("layout_columnWeight") || attrName.startsWith("layout_rowWeight")) {
            return Arrays.asList("GridLayout");
        }
        if (attrName.startsWith("layout_align") || attrName.startsWith("layout_center") ||
            attrName.startsWith("layout_to") || attrName.startsWith("layout_above") ||
            attrName.startsWith("layout_below") || attrName.startsWith("layout_baseline") ||
            attrName.equals("layout_alignWithParentIfMissing")) {
            return Arrays.asList("RelativeLayout");
        }
        if (attrName.startsWith("layout_constraint") || attrName.startsWith("layout_goneMargin") ||
            attrName.startsWith("layout_editor_absolute") || attrName.equals("layout_wrapBehaviorInParent")) {
            return Arrays.asList("ConstraintLayout", "MotionLayout");
        }
        if (attrName.startsWith("layout_anchor") || attrName.equals("layout_behavior") ||
            attrName.equals("layout_keyline") || attrName.equals("layout_insetEdge") ||
            attrName.equals("layout_dodgeInsetEdges")) {
            return Arrays.asList("CoordinatorLayout");
        }
        if (attrName.equals("layout_collapseMode") || attrName.equals("layout_collapseParallaxMultiplier")) {
            return Arrays.asList("CollapsingToolbarLayout");
        }
        if (attrName.equals("layout_scrollFlags")) {
            return Arrays.asList("AppBarLayout");
        }
        if (attrName.equals("layout_widthPercent") || attrName.equals("layout_heightPercent") ||
            attrName.equals("layout_marginPercent") || attrName.equals("layout_aspectRatio")) {
            return Arrays.asList("PercentFrameLayout", "PercentRelativeLayout");
        }
        return null;
    }

    private static String getLocalName(String name) {
        int colon = name.indexOf(':');
        if (colon != -1) {
            name = name.substring(colon + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(dot + 1);
        }
        return name;
    }
}