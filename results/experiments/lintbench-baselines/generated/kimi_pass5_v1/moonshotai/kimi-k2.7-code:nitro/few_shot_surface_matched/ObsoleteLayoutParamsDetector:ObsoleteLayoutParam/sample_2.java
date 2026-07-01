package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move "
                            + "view code around without updating the layout params. This will cause "
                            + "useless attribute processing at runtime, and is misleading for others "
                            + "reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    4,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.ALL_RESOURCE_FILES));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LAYOUT_PREFIX = "layout_";

    private static final Map<String, Set<String>> LAYOUT_PARAMS;

    static {
        Map<String, Set<String>> map = new HashMap<>();

        Set<String> baseMargins =
                new HashSet<>(
                        Arrays.asList(
                                "layout_width",
                                "layout_height",
                                "layout_margin",
                                "layout_marginLeft",
                                "layout_marginRight",
                                "layout_marginTop",
                                "layout_marginBottom",
                                "layout_marginStart",
                                "layout_marginEnd",
                                "layout_marginHorizontal",
                                "layout_marginVertical"));

        map.put("LinearLayout", with(baseMargins, "layout_weight", "layout_gravity"));
        map.put("RadioGroup", with(baseMargins, "layout_weight", "layout_gravity"));
        map.put("FrameLayout", with(baseMargins, "layout_gravity"));
        map.put("DrawerLayout", with(baseMargins, "layout_gravity"));
        map.put("androidx.drawerlayout.widget.DrawerLayout", with(baseMargins, "layout_gravity"));
        map.put("SlidingPaneLayout", with(baseMargins, "layout_weight"));
        map.put(
                "androidx.slidingpanelayout.widget.SlidingPaneLayout",
                with(baseMargins, "layout_weight"));

        map.put(
                "RelativeLayout",
                with(
                        baseMargins,
                        "layout_above",
                        "layout_below",
                        "layout_toLeftOf",
                        "layout_toRightOf",
                        "layout_toStartOf",
                        "layout_toEndOf",
                        "layout_alignTop",
                        "layout_alignBottom",
                        "layout_alignLeft",
                        "layout_alignRight",
                        "layout_alignStart",
                        "layout_alignEnd",
                        "layout_alignBaseline",
                        "layout_alignParentTop",
                        "layout_alignParentBottom",
                        "layout_alignParentLeft",
                        "layout_alignParentRight",
                        "layout_alignParentStart",
                        "layout_alignParentEnd",
                        "layout_centerHorizontal",
                        "layout_centerVertical",
                        "layout_centerInParent",
                        "layout_alignWithParentIfMissing"));

        map.put(
                "GridLayout",
                with(
                        baseMargins,
                        "layout_gravity",
                        "layout_row",
                        "layout_column",
                        "layout_rowSpan",
                        "layout_columnSpan"));
        map.put(
                "TableRow",
                with(baseMargins, "layout_weight", "layout_gravity", "layout_column", "layout_span"));

        map.put(
                "CoordinatorLayout",
                with(
                        baseMargins,
                        "layout_behavior",
                        "layout_anchor",
                        "layout_anchorGravity",
                        "layout_dodgeInsetEdges",
                        "layout_insetEdge",
                        "layout_keyline",
                        "layout_scrollFlags",
                        "layout_scrollInterpolator"));
        map.put(
                "androidx.coordinatorlayout.widget.CoordinatorLayout",
                with(
                        baseMargins,
                        "layout_behavior",
                        "layout_anchor",
                        "layout_anchorGravity",
                        "layout_dodgeInsetEdges",
                        "layout_insetEdge",
                        "layout_keyline",
                        "layout_scrollFlags",
                        "layout_scrollInterpolator"));

        map.put("ConstraintLayout", constraintSet(baseMargins));
        map.put("androidx.constraintlayout.widget.ConstraintLayout", constraintSet(baseMargins));

        map.put(
                "FlexboxLayout",
                with(
                        baseMargins,
                        "layout_order",
                        "layout_flexGrow",
                        "layout_flexShrink",
                        "layout_alignSelf",
                        "layout_minWidth",
                        "layout_minHeight",
                        "layout_maxWidth",
                        "layout_maxHeight",
                        "layout_wrapBefore"));
        map.put(
                "com.google.android.flexbox.FlexboxLayout",
                with(
                        baseMargins,
                        "layout_order",
                        "layout_flexGrow",
                        "layout_flexShrink",
                        "layout_alignSelf",
                        "layout_minWidth",
                        "layout_minHeight",
                        "layout_maxWidth",
                        "layout_maxHeight",
                        "layout_wrapBefore"));

        LAYOUT_PARAMS = Collections.unmodifiableMap(map);
    }

    private static Set<String> with(Set<String> base, String... extras) {
        Set<String> set = new HashSet<>(base);
        Collections.addAll(set, extras);
        return Collections.unmodifiableSet(set);
    }

    private static Set<String> constraintSet(Set<String> base) {
        Set<String> set = new HashSet<>(base);
        set.addAll(
                Arrays.asList(
                        "layout_goneMarginLeft",
                        "layout_goneMarginRight",
                        "layout_goneMarginTop",
                        "layout_goneMarginBottom",
                        "layout_goneMarginStart",
                        "layout_goneMarginEnd",
                        "layout_goneMarginBaseline",
                        "layout_marginBaseline",
                        "layout_goneMarginHorizontal",
                        "layout_goneMarginVertical"));
        return Collections.unmodifiableSet(set);
    }

    private final List<PendingAttribute> mPending = new ArrayList<>();

    private static class PendingAttribute {
        final XmlContext context;
        final Attr attribute;
        final String parentTag;

        PendingAttribute(XmlContext context, Attr attribute, String parentTag) {
            this.context = context;
            this.attribute = attribute;
            this.parentTag = parentTag;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        if (name == null || !name.startsWith(LAYOUT_PREFIX)) {
            return;
        }

        if ("layout_width".equals(name) || "layout_height".equals(name)) {
            return;
        }

        Node parentNode = attribute.getOwnerElement().getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        String parentTag = ((Element) parentNode).getTagName();
        if (parentTag == null) {
            return;
        }

        if (!isSupported(parentTag, name)) {
            mPending.add(new PendingAttribute(context, attribute, parentTag));
        }
    }

    private static boolean isSupported(String parentTag, String paramName) {
        Set<String> params = LAYOUT_PARAMS.get(parentTag);
        if (params == null) {
            return true;
        }
        if (params.contains(paramName)) {
            return true;
        }
        if ("ConstraintLayout".equals(parentTag)
                || "androidx.constraintlayout.widget.ConstraintLayout".equals(parentTag)) {
            return paramName.startsWith("layout_constraint");
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (PendingAttribute pending : mPending) {
            pending.context.report(
                    ISSUE,
                    pending.attribute,
                    pending.context.getLocation(pending.attribute),
                    "Invalid layout param in a "
                            + pending.parentTag
                            + ": '"
                            + pending.attribute.getLocalName()
                            + "' is not defined for "
                            + pending.parentTag);
        }
        mPending.clear();
    }
}