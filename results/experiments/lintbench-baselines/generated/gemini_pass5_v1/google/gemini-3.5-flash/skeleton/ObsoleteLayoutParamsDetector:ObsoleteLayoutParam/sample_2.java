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
import java.util.Collections;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no effect. "
                    + "This usually happens when you change the parent layout or move view code around "
                    + "without updating the layout params. This will cause useless attribute processing "
                    + "at runtime, and is misleading for others reading the layout so the parameter "
                    + "should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Set<String> UNIVERSAL_PARAMS = new HashSet<>(Arrays.asList(
            "layout_width",
            "layout_height",
            "layout_margin",
            "layout_marginLeft",
            "layout_marginTop",
            "layout_marginRight",
            "layout_marginBottom",
            "layout_marginStart",
            "layout_marginEnd"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton("*");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handled in visitElement for efficiency
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        if ("merge".equals(parentTag)) {
            return;
        }

        String parentSimpleName = getSimpleName(parentTag);
        String baseKey = getBaseLayoutKey(parentSimpleName);
        if (baseKey == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node item = attributes.item(i);
            if (item instanceof Attr) {
                Attr attribute = (Attr) item;
                String name = attribute.getLocalName();
                if (name != null && name.startsWith("layout_")) {
                    if (UNIVERSAL_PARAMS.contains(name)) {
                        continue;
                    }
                    if (!isParamAllowedForParent(baseKey, name)) {
                        String message = String.format(
                                "Layout parameter `%s` is ignored by parent `%s`",
                                attribute.getName(), parentTag);
                        context.report(ISSUE, attribute, context.getLocation(attribute), message);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    private static String getSimpleName(String tag) {
        int dot = tag.lastIndexOf('.');
        if (dot != -1) {
            return tag.substring(dot + 1);
        }
        return tag;
    }

    private static String getBaseLayoutKey(String parentSimpleName) {
        if (parentSimpleName.contains("Percent")) {
            return null;
        }
        if (parentSimpleName.contains("LinearLayout") || parentSimpleName.equals("RadioGroup") || parentSimpleName.equals("TableLayout")) {
            return "LinearLayout";
        }
        if (parentSimpleName.contains("FrameLayout") || parentSimpleName.contains("ScrollView") || parentSimpleName.equals("CardView") || parentSimpleName.endsWith("Switcher") || parentSimpleName.endsWith("Animator") || parentSimpleName.endsWith("Flipper")) {
            return "FrameLayout";
        }
        if (parentSimpleName.contains("RelativeLayout")) {
            return "RelativeLayout";
        }
        if (parentSimpleName.contains("ConstraintLayout") || parentSimpleName.equals("MotionLayout")) {
            return "ConstraintLayout";
        }
        if (parentSimpleName.contains("CoordinatorLayout")) {
            return "CoordinatorLayout";
        }
        if (parentSimpleName.contains("DrawerLayout")) {
            return "DrawerLayout";
        }
        if (parentSimpleName.equals("TableRow")) {
            return "TableRow";
        }
        if (parentSimpleName.contains("GridLayout")) {
            return "GridLayout";
        }
        if (parentSimpleName.equals("AbsoluteLayout")) {
            return "AbsoluteLayout";
        }
        if (parentSimpleName.contains("ViewPager")) {
            return "ViewPager";
        }
        if (parentSimpleName.contains("RecyclerView") || parentSimpleName.contains("ListView") || parentSimpleName.contains("GridView")) {
            return "RecyclerView";
        }
        return null;
    }

    private static boolean isParamAllowedForParent(String key, String param) {
        switch (key) {
            case "LinearLayout":
                return "layout_weight".equals(param) || "layout_gravity".equals(param);
            case "FrameLayout":
            case "DrawerLayout":
            case "ViewPager":
                return "layout_gravity".equals(param);
            case "RelativeLayout":
                return param.equals("layout_alignParentLeft")
                        || param.equals("layout_alignParentTop")
                        || param.equals("layout_alignParentRight")
                        || param.equals("layout_alignParentBottom")
                        || param.equals("layout_centerInParent")
                        || param.equals("layout_centerHorizontal")
                        || param.equals("layout_centerVertical")
                        || param.equals("layout_toLeftOf")
                        || param.equals("layout_toRightOf")
                        || param.equals("layout_above")
                        || param.equals("layout_below")
                        || param.equals("layout_alignLeft")
                        || param.equals("layout_alignTop")
                        || param.equals("layout_alignRight")
                        || param.equals("layout_alignBottom")
                        || param.equals("layout_alignBaseline")
                        || param.equals("layout_toStartOf")
                        || param.equals("layout_toEndOf")
                        || param.equals("layout_alignStart")
                        || param.equals("layout_alignEnd");
            case "ConstraintLayout":
                return param.startsWith("layout_constraint")
                        || param.startsWith("layout_goneMargin")
                        || param.startsWith("layout_editor_")
                        || param.equals("layout_wrapBehaviorInParent");
            case "CoordinatorLayout":
                return param.equals("layout_behavior")
                        || param.equals("layout_anchor")
                        || param.equals("layout_anchorGravity")
                        || param.equals("layout_insetEdge")
                        || param.equals("layout_dodgeInsetEdges")
                        || param.equals("layout_keyline");
            case "TableRow":
                return param.equals("layout_column") || param.equals("layout_span");
            case "GridLayout":
                return param.equals("layout_row")
                        || param.equals("layout_rowSpan")
                        || param.equals("layout_column")
                        || param.equals("layout_columnSpan")
                        || param.equals("layout_gravity")
                        || param.equals("layout_rowWeight")
                        || param.equals("layout_columnWeight");
            case "AbsoluteLayout":
                return param.equals("layout_x") || param.equals("layout_y");
            case "RecyclerView":
                return false;
            default:
                return true;
        }
    }
}