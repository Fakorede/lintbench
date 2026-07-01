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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no effect. " +
                    "This usually happens when you change the parent layout or move view code around without " +
                    "updating the layout params. This will cause useless attribute processing at runtime, " +
                    "and is misleading for others reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Map<String, Set<String>> ALLOWED_PARAMS = new HashMap<>();
    private static final Set<String> UNIVERSAL_PARAMS = new HashSet<>(Arrays.asList(
            "layout_width", "layout_height",
            "layout_margin", "layout_marginLeft", "layout_marginTop", "layout_marginRight", "layout_marginBottom",
            "layout_marginStart", "layout_marginEnd", "layout_marginHorizontal", "layout_marginVertical",
            "layout_marginBaseline",
            "layout_animation", "layout_transitionGroup", "layoutMode", "layoutDirection"
    ));

    static {
        ALLOWED_PARAMS.put("LinearLayout", new HashSet<>(Arrays.asList("layout_weight", "layout_gravity")));
        ALLOWED_PARAMS.put("FrameLayout", new HashSet<>(Arrays.asList("layout_gravity")));
        ALLOWED_PARAMS.put("DrawerLayout", new HashSet<>(Arrays.asList("layout_gravity")));
        ALLOWED_PARAMS.put("ViewPager", new HashSet<>(Arrays.asList("layout_gravity")));
        ALLOWED_PARAMS.put("ViewPager2", new HashSet<>(Arrays.asList("layout_gravity")));
        ALLOWED_PARAMS.put("CoordinatorLayout", new HashSet<>(Arrays.asList(
                "layout_behavior", "layout_anchor", "layout_anchorGravity", "layout_keyline",
                "layout_insetEdge", "layout_dodgeInsetEdges", "layout_gravity")));
        ALLOWED_PARAMS.put("RelativeLayout", new HashSet<>(Arrays.asList(
                "layout_above", "layout_alignBaseline", "layout_alignBottom", "layout_alignEnd",
                "layout_alignLeft", "layout_alignParentBottom", "layout_alignParentEnd",
                "layout_alignParentLeft", "layout_alignParentRight", "layout_alignParentStart",
                "layout_alignParentTop", "layout_alignRight", "layout_alignStart", "layout_alignTop",
                "layout_below", "layout_centerHorizontal", "layout_centerInParent", "layout_centerVertical",
                "layout_toEndOf", "layout_toLeftOf", "layout_toRightOf", "layout_toStartOf",
                "layout_alignWithParentIfMissing")));
        ALLOWED_PARAMS.put("GridLayout", new HashSet<>(Arrays.asList(
                "layout_row", "layout_column", "layout_rowSpan", "layout_columnSpan",
                "layout_rowWeight", "layout_columnWeight", "layout_gravity")));
        ALLOWED_PARAMS.put("TableRow", new HashSet<>(Arrays.asList("layout_column", "layout_span")));
        ALLOWED_PARAMS.put("AbsoluteLayout", new HashSet<>(Arrays.asList("layout_x", "layout_y")));
    }

    private static String getSimpleTagName(@NonNull Element element) {
        String tag = element.getTagName();
        int dot = tag.lastIndexOf('.');
        return dot == -1 ? tag : tag.substring(dot + 1);
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
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent == null) {
            return;
        }

        String parentTag = getSimpleTagName(parent);
        if ("merge".equals(parentTag) || "include".equals(parentTag)) {
            return;
        }

        Set<String> allowed = ALLOWED_PARAMS.get(parentTag);
        boolean isConstraintParent = "ConstraintLayout".equals(parentTag) || "MotionLayout".equals(parentTag);

        // Skip unknown/custom parents to avoid false positives
        if (allowed == null && !isConstraintParent) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
                int colon = localName.indexOf(':');
                if (colon != -1) {
                    localName = localName.substring(colon + 1);
                }
            }

            if (!localName.startsWith("layout_")) {
                continue;
            }

            if (UNIVERSAL_PARAMS.contains(localName)) {
                continue;
            }

            boolean isValid = false;
            if (allowed != null && allowed.contains(localName)) {
                isValid = true;
            } else if (isConstraintParent) {
                if (localName.startsWith("layout_constraint") ||
                    localName.startsWith("layout_goneMargin") ||
                    localName.startsWith("layout_editor") ||
                    localName.startsWith("layout_wrapBehaviorInParent") ||
                    localName.startsWith("layout_constrained")) {
                    isValid = true;
                }
            }

            if (!isValid) {
                context.report(ISSUE, context.getLocation(attr),
                        "Invalid layout param in a `" + parentTag + "`: `" + localName + "`");
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Logic handled in visitElement to access parent context efficiently
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }
}