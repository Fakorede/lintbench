package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

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
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.LAYOUT_RESOURCE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Required override
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        if (name.equals("layout_width") || name.equals("layout_height")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        if (isObsolete(name, parentTag)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("Invalid layout param `%s` for parent `%s`", attribute.getName(), parentTag));
        }
    }

    private static boolean isObsolete(String attributeName, String parentTag) {
        if (parentTag.equals("LinearLayout")) {
            if (isRelativeLayoutParam(attributeName)) {
                return true;
            }
        } else if (parentTag.equals("RelativeLayout")) {
            if (attributeName.equals("layout_weight")) {
                return true;
            }
        } else if (parentTag.equals("FrameLayout")) {
            if (attributeName.equals("layout_weight") || isRelativeLayoutParam(attributeName)) {
                return true;
            }
        } else if (parentTag.equals("GridLayout")) {
            if (attributeName.equals("layout_weight") || isRelativeLayoutParam(attributeName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRelativeLayoutParam(String name) {
        return name.startsWith("layout_align")
                || name.startsWith("layout_to")
                || name.startsWith("layout_above")
                || name.startsWith("layout_below")
                || name.startsWith("layout_center")
                || name.equals("layout_alignParentLeft")
                || name.equals("layout_alignParentRight")
                || name.equals("layout_alignParentTop")
                || name.equals("layout_alignParentBottom")
                || name.equals("layout_alignParentStart")
                || name.equals("layout_alignParentEnd");
    }

    @Override
    public void afterCheckRootProject(Context context) {
        // Required override
    }
}