package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

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
                    5,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Hierarchy traversal is handled on-demand in visitAttribute via DOM parent lookup.
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String ns = attribute.getNamespaceURI();
        if (!"http://schemas.android.com/apk/res/android".equals(ns)) {
            return;
        }

        String name = attribute.getLocalName();
        if (!name.startsWith("layout_")) {
            return;
        }
        if (name.equals("layout_width") || name.equals("layout_height")) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        Node parentNode = owner.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        String parentTag = ((Element) parentNode).getTagName();
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex != -1) {
            parentTag = parentTag.substring(dotIndex + 1);
        }

        String param = name.substring("layout_".length());
        if (!isValidLayoutParams(param, parentTag)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Invalid layout param in a " + parentTag + ": " + name);
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        // No persistent state to clean up.
    }

    private static boolean isValidLayoutParams(String param, String parentTag) {
        switch (parentTag) {
            case "LinearLayout":
                return param.equals("weight") || param.equals("gravity");
            case "FrameLayout":
            case "DrawerLayout":
                return param.equals("gravity");
            case "RelativeLayout":
                return param.startsWith("align")
                        || param.startsWith("center")
                        || param.startsWith("to")
                        || param.equals("above")
                        || param.equals("below")
                        || param.equals("alignWithParentIfMissing");
            case "GridLayout":
                return param.equals("row")
                        || param.equals("column")
                        || param.equals("rowSpan")
                        || param.equals("columnSpan")
                        || param.equals("gravity");
            case "ConstraintLayout":
                return param.startsWith("constraint")
                        || param.startsWith("goneMargin")
                        || param.startsWith("editor_absolute")
                        || param.equals("wrapBehaviorInParent");
            case "CoordinatorLayout":
                return param.equals("anchor")
                        || param.equals("anchorGravity")
                        || param.equals("behavior")
                        || param.equals("keyline")
                        || param.equals("insetEdge")
                        || param.equals("dodgeInsetEdges");
            case "TableLayout":
                return param.equals("collapseColumns")
                        || param.equals("shrinkColumns")
                        || param.equals("stretchColumns");
            case "TableRow":
                return param.equals("span");
            default:
                return false;
        }
    }
}