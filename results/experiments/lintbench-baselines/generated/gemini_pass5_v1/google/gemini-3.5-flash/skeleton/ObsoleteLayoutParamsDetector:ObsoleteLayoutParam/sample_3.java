package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Arrays;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no effect. " +
                    "This usually happens when you change the parent layout or move view code around " +
                    "without updating the layout params. This will cause useless attribute processing " +
                    "at runtime, and is misleading for others reading the layout so the parameter " +
                    "should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                "layout_gravity",
                "layout_weight",
                "layout_alignParentLeft",
                "layout_alignParentRight",
                "layout_alignParentTop",
                "layout_alignParentBottom",
                "layout_alignParentStart",
                "layout_alignParentEnd",
                "layout_centerInParent",
                "layout_centerHorizontal",
                "layout_centerVertical",
                "layout_toLeftOf",
                "layout_toRightOf",
                "layout_toStartOf",
                "layout_toEndOf",
                "layout_above",
                "layout_below",
                "layout_alignLeft",
                "layout_alignRight",
                "layout_alignTop",
                "layout_alignBottom",
                "layout_alignBaseline",
                "layout_alignStart",
                "layout_alignEnd"
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        org.w3c.dom.Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        String simpleParent = getSimpleTagName(parentTag);

        if (simpleParent.equals("merge")) {
            return;
        }

        String msg = null;

        if (simpleParent.equals("LinearLayout") || simpleParent.equals("RadioGroup")) {
            if (isRelativeLayoutParam(name)) {
                msg = String.format("Invalid layout param `%s` in a `%s` parent", name, simpleParent);
            }
        } else if (simpleParent.equals("RelativeLayout")) {
            if (name.equals("layout_gravity")) {
                msg = "RelativeLayout does not support `layout_gravity` (use layout_centerHorizontal, layout_alignParentLeft, etc.)";
            } else if (name.equals("layout_weight")) {
                msg = "RelativeLayout does not support `layout_weight`";
            }
        } else if (simpleParent.equals("FrameLayout") || simpleParent.equals("ScrollView") || simpleParent.equals("HorizontalScrollView")) {
            if (isRelativeLayoutParam(name)) {
                msg = String.format("Invalid layout param `%s` in a `%s` parent", name, simpleParent);
            } else if (name.equals("layout_weight")) {
                msg = String.format("`layout_weight` has no effect in a `%s` parent", simpleParent);
            }
        } else if (simpleParent.equals("ConstraintLayout")) {
            if (isRelativeLayoutParam(name)) {
                msg = "ConstraintLayout does not support RelativeLayout layout params";
            } else if (name.equals("layout_weight")) {
                msg = "ConstraintLayout does not support `layout_weight`";
            } else if (name.equals("layout_gravity")) {
                msg = "ConstraintLayout does not support `layout_gravity`";
            }
        } else if (simpleParent.equals("CoordinatorLayout")) {
            if (isRelativeLayoutParam(name)) {
                msg = "CoordinatorLayout does not support RelativeLayout layout params";
            } else if (name.equals("layout_weight")) {
                msg = "CoordinatorLayout does not support `layout_weight`";
            }
        } else if (simpleParent.equals("DrawerLayout")) {
            if (isRelativeLayoutParam(name)) {
                msg = "DrawerLayout does not support RelativeLayout layout params";
            } else if (name.equals("layout_weight")) {
                msg = "DrawerLayout does not support `layout_weight`";
            }
        } else if (simpleParent.equals("GridLayout")) {
            if (isRelativeLayoutParam(name)) {
                msg = "GridLayout does not support RelativeLayout layout params";
            } else if (name.equals("layout_weight")) {
                msg = "GridLayout does not support `layout_weight`";
            }
        }

        if (msg != null) {
            context.report(ISSUE, attribute, context.getLocation(attribute), msg);
        }
    }

    private static String getSimpleTagName(String tag) {
        int idx = tag.lastIndexOf('.');
        if (idx != -1) {
            return tag.substring(idx + 1);
        }
        return tag;
    }

    private static boolean isRelativeLayoutParam(String name) {
        switch (name) {
            case "layout_alignParentLeft":
            case "layout_alignParentRight":
            case "layout_alignParentTop":
            case "layout_alignParentBottom":
            case "layout_alignParentStart":
            case "layout_alignParentEnd":
            case "layout_centerInParent":
            case "layout_centerHorizontal":
            case "layout_centerVertical":
            case "layout_toLeftOf":
            case "layout_toRightOf":
            case "layout_toStartOf":
            case "layout_toEndOf":
            case "layout_above":
            case "layout_below":
            case "layout_alignLeft":
            case "layout_alignRight":
            case "layout_alignTop":
            case "layout_alignBottom":
            case "layout_alignBaseline":
            case "layout_alignStart":
            case "layout_alignEnd":
                return true;
            default:
                return false;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
    }
}