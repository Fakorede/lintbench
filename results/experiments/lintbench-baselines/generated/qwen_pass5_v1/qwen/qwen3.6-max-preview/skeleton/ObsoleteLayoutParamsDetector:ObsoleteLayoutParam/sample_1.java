package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no effect. This usually happens when you change the parent layout or move view code around without updating the layout params. This will cause useless attribute processing at runtime, and is misleading for others reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parent = element.getParentNode();
        if (!(parent instanceof Element)) {
            return;
        }

        Element parentElement = (Element) parent;
        String parentTag = parentElement.getTagName();

        if (parentTag.equals("merge") || parentTag.equals("include")) {
            return;
        }

        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex != -1) {
            parentTag = parentTag.substring(dotIndex + 1);
        }

        if (isObsolete(name, parentTag)) {
            String message = String.format(
                    "Invalid layout param `%1$s` in a `%2$s`", name, parentTag);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handled via visitAttribute
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    private static boolean isObsolete(@NonNull String attr, @NonNull String parent) {
        if (attr.startsWith("layout_constraint")) {
            return !parent.equals("ConstraintLayout") && !parent.equals("ConstraintSet");
        }
        if (attr.startsWith("layout_align") || attr.equals("layout_below") || attr.equals("layout_above") ||
            attr.equals("layout_toLeftOf") || attr.equals("layout_toRightOf") ||
            attr.equals("layout_toStartOf") || attr.equals("layout_toEndOf") ||
            attr.equals("layout_centerHorizontal") || attr.equals("layout_centerVertical") || attr.equals("layout_centerInParent")) {
            return !parent.equals("RelativeLayout");
        }
        if (attr.equals("layout_weight")) {
            return !parent.equals("LinearLayout");
        }
        if (attr.equals("layout_gravity")) {
            return !(parent.equals("FrameLayout") || parent.equals("LinearLayout") || parent.equals("GridLayout") ||
                     parent.equals("DrawerLayout") || parent.equals("CoordinatorLayout") || parent.equals("Toolbar"));
        }
        if (attr.startsWith("layout_row") || attr.startsWith("layout_column")) {
            return !(parent.equals("GridLayout") || parent.equals("TableRow"));
        }
        if (attr.equals("layout_span")) {
            return !parent.equals("TableRow");
        }
        if (attr.equals("layout_behavior") || attr.equals("layout_anchor") || attr.equals("layout_anchorGravity") ||
            attr.equals("layout_dodgeInsetEdges") || attr.equals("layout_insetEdge") || attr.equals("layout_keyline")) {
            return !parent.equals("CoordinatorLayout");
        }
        if (attr.equals("layout_scrollFlags") || attr.equals("layout_scrollInterpolator")) {
            return !parent.equals("AppBarLayout");
        }
        if (attr.startsWith("layout_flex")) {
            return !parent.equals("FlexboxLayout");
        }
        return false;
    }
}