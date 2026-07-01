package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
        "ObsoleteLayoutParam",
        "Obsolete layout params",
        "The given layout_param is not defined for the given layout, meaning it has no "
            + "effect. This usually happens when you change the parent layout or move view "
            + "code around without updating the layout params. This will cause useless "
            + "attribute processing at runtime, and is misleading for others reading the "
            + "layout so the parameter should be removed.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            ObsoleteLayoutParamsDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    private static final Set<String> RELATIVE_LAYOUT_ATTRS = new HashSet<>();
    static {
        RELATIVE_LAYOUT_ATTRS.add("layout_alignParentLeft");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignParentTop");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignParentRight");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignParentBottom");
        RELATIVE_LAYOUT_ATTRS.add("layout_centerInParent");
        RELATIVE_LAYOUT_ATTRS.add("layout_centerHorizontal");
        RELATIVE_LAYOUT_ATTRS.add("layout_centerVertical");
        RELATIVE_LAYOUT_ATTRS.add("layout_toLeftOf");
        RELATIVE_LAYOUT_ATTRS.add("layout_toRightOf");
        RELATIVE_LAYOUT_ATTRS.add("layout_above");
        RELATIVE_LAYOUT_ATTRS.add("layout_below");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignBaseline");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignLeft");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignTop");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignRight");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignBottom");
        RELATIVE_LAYOUT_ATTRS.add("layout_toStartOf");
        RELATIVE_LAYOUT_ATTRS.add("layout_toEndOf");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignParentStart");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignParentEnd");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignStart");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignEnd");
        RELATIVE_LAYOUT_ATTRS.add("layout_alignWithParentIfMissing");
    }

    private static final Set<String> GRID_LAYOUT_ATTRS = new HashSet<>();
    static {
        GRID_LAYOUT_ATTRS.add("layout_row");
        GRID_LAYOUT_ATTRS.add("layout_rowSpan");
        GRID_LAYOUT_ATTRS.add("layout_columnSpan");
        GRID_LAYOUT_ATTRS.add("layout_rowWeight");
        GRID_LAYOUT_ATTRS.add("layout_columnWeight");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        if ("merge".equals(parentTag) || "include".equals(parentTag)) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null || !name.startsWith("layout_")) {
                continue;
            }

            String obsoleteIn = getObsoleteParentMessage(name, parentTag);
            if (obsoleteIn != null) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    String.format("The `%s` attribute is obsolete on a child of `%s`", name, obsoleteIn)
                );
            }
        }
    }

    @Nullable
    private static String getObsoleteParentMessage(String attrName, String parentTag) {
        boolean isRelative = isRelativeLayout(parentTag);
        boolean isLinear = isLinearLayout(parentTag);
        boolean isFrame = isFrameLayout(parentTag);
        boolean isConstraint = isConstraintLayout(parentTag);
        boolean isGrid = isGridLayout(parentTag);
        boolean isTable = isTableLayout(parentTag);
        boolean isTableRow = isTableRow(parentTag);
        boolean isCoordinator = isCoordinatorLayout(parentTag);

        boolean recognized = isRelative || isLinear || isFrame || isConstraint || isGrid || isTable || isTableRow || isCoordinator;
        if (!recognized) {
            return null;
        }

        if (RELATIVE_LAYOUT_ATTRS.contains(attrName)) {
            if (!isRelative) {
                return parentTag;
            }
        }

        if (attrName.startsWith("layout_constraint") || attrName.startsWith("layout_goneMargin")) {
            if (!isConstraint) {
                return parentTag;
            }
        }

        if ("layout_weight".equals(attrName)) {
            if (!isLinear) {
                return parentTag;
            }
        }

        if (GRID_LAYOUT_ATTRS.contains(attrName)) {
            if (!isGrid) {
                return parentTag;
            }
        }

        if ("layout_column".equals(attrName)) {
            if (!isGrid && !isTableRow) {
                return parentTag;
            }
        }

        if ("layout_span".equals(attrName)) {
            if (!isTableRow) {
                return parentTag;
            }
        }

        if ("layout_gravity".equals(attrName)) {
            if (isRelative || isConstraint || isTable) {
                return parentTag;
            }
        }

        return null;
    }

    private static boolean isRelativeLayout(String tag) {
        return tag.equals("RelativeLayout") || tag.endsWith(".RelativeLayout")
            || tag.equals("PercentRelativeLayout") || tag.endsWith(".PercentRelativeLayout");
    }

    private static boolean isLinearLayout(String tag) {
        return tag.equals("LinearLayout") || tag.endsWith(".LinearLayout")
            || tag.equals("RadioGroup") || tag.endsWith(".RadioGroup")
            || tag.equals("TableRow") || tag.endsWith(".TableRow")
            || tag.equals("ActionMenuView") || tag.endsWith(".ActionMenuView")
            || tag.equals("SearchView") || tag.endsWith(".SearchView");
    }

    private static boolean isFrameLayout(String tag) {
        return tag.equals("FrameLayout") || tag.endsWith(".FrameLayout")
            || tag.equals("ScrollView") || tag.endsWith(".ScrollView")
            || tag.equals("NestedScrollView") || tag.endsWith(".NestedScrollView")
            || tag.equals("CardView") || tag.endsWith(".CardView")
            || tag.equals("DrawerLayout") || tag.endsWith(".DrawerLayout");
    }

    private static boolean isConstraintLayout(String tag) {
        return tag.equals("ConstraintLayout") || tag.endsWith(".ConstraintLayout")
            || tag.contains("ConstraintLayout");
    }

    private static boolean isGridLayout(String tag) {
        return tag.equals("GridLayout") || tag.endsWith(".GridLayout");
    }

    private static boolean isTableLayout(String tag) {
        return tag.equals("TableLayout") || tag.endsWith(".TableLayout");
    }

    private static boolean isTableRow(String tag) {
        return tag.equals("TableRow") || tag.endsWith(".TableRow");
    }

    private static boolean isCoordinatorLayout(String tag) {
        return tag.equals("CoordinatorLayout") || tag.endsWith(".CoordinatorLayout");
    }
}