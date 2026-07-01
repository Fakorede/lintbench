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
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
        "ObsoleteLayoutParam",
        "Obsolete layout params",
        "The given layout_param is not defined for the given layout, meaning it has no effect. " +
        "This usually happens when you change the parent layout or move view code around without " +
        "updating the layout params. This will cause useless attribute processing at runtime, and " +
        "is misleading for others reading the layout so the parameter should be removed.",
        Category.CORRECTNESS, 6, Severity.WARNING,
        new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Set<String> RELATIVE_LAYOUT_PARAMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "layout_above", "layout_alignBaseline", "layout_alignBottom", "layout_alignEnd", "layout_alignLeft",
        "layout_alignParentBottom", "layout_alignParentEnd", "layout_alignParentLeft", "layout_alignParentRight",
        "layout_alignParentStart", "layout_alignParentTop", "layout_alignRight", "layout_alignStart",
        "layout_alignTop", "layout_alignWithParentIfMissing", "layout_below", "layout_centerHorizontal",
        "layout_centerInParent", "layout_centerVertical", "layout_toEndOf", "layout_toLeftOf",
        "layout_toRightOf", "layout_toStartOf"
    )));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent == null) {
            return;
        }

        String parentTag = parent.getTagName();
        if (parentTag.contains(".")) {
            parentTag = parentTag.substring(parentTag.lastIndexOf('.') + 1);
        }

        if (parentTag.equals(SdkConstants.TAG_MERGE) || parentTag.equals(SdkConstants.TAG_INCLUDE)) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String ns = attr.getNamespaceURI();
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
            }

            if (SdkConstants.ANDROID_URI.equals(ns) && name.startsWith(SdkConstants.LAYOUT_RESOURCE_PREFIX)) {
                if (!isValidParam(parentTag, name)) {
                    String message = String.format(
                        "Invalid layout param '%1$s' in a '%2$s'", name, parentTag);
                    context.report(ISSUE, attr, context.getNameLocation(attr), message);
                }
            }
        }
    }

    private static boolean isValidParam(@NonNull String parentClass, @NonNull String attrName) {
        if (attrName.equals(SdkConstants.ATTR_LAYOUT_WIDTH) || attrName.equals(SdkConstants.ATTR_LAYOUT_HEIGHT)) {
            return true;
        }
        if (attrName.startsWith("layout_margin")) {
            return true;
        }

        switch (parentClass) {
            case "LinearLayout":
                return attrName.equals("layout_weight") || attrName.equals("layout_gravity");
            case "FrameLayout":
            case "DrawerLayout":
                return attrName.equals("layout_gravity");
            case "RelativeLayout":
                return RELATIVE_LAYOUT_PARAMS.contains(attrName);
            case "GridLayout":
                return attrName.equals("layout_row") || attrName.equals("layout_rowSpan") || attrName.equals("layout_rowWeight")
                    || attrName.equals("layout_column") || attrName.equals("layout_columnSpan") || attrName.equals("layout_columnWeight")
                    || attrName.equals("layout_gravity");
            case "ConstraintLayout":
                return attrName.startsWith("layout_constraint") || attrName.startsWith("layout_goneMargin")
                    || attrName.equals("layout_marginBaseline") || attrName.equals("layout_wrapBehaviorInParent")
                    || attrName.equals("layout_editor_absoluteX") || attrName.equals("layout_editor_absoluteY");
            case "CoordinatorLayout":
                return attrName.equals("layout_behavior") || attrName.equals("layout_anchor") || attrName.equals("layout_anchorGravity")
                    || attrName.equals("layout_keyline") || attrName.equals("layout_insetEdge") || attrName.equals("layout_dodgeInsetEdges")
                    || attrName.equals("layout_gravity");
            case "TableRow":
                return attrName.equals("layout_column") || attrName.equals("layout_span");
            case "AbsoluteLayout":
                return attrName.equals("layout_x") || attrName.equals("layout_y");
            case "AppBarLayout":
                return attrName.equals("layout_scrollFlags");
            case "CollapsingToolbarLayout":
                return attrName.equals("layout_collapseMode") || attrName.equals("layout_collapseParallaxMultiplier");
            default:
                return true;
        }
    }
}