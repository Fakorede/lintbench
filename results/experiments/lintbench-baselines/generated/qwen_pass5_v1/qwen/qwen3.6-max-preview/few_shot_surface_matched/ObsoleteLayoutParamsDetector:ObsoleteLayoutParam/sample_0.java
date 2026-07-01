package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                    new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<Element, List<Attr>> mLayoutParams = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name != null && name.startsWith("layout_") && !isUniversalParam(name)) {
            Element owner = attribute.getOwnerElement();
            mLayoutParams.computeIfAbsent(owner, k -> new ArrayList<>()).add(attribute);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            List<Attr> attrs = mLayoutParams.get(element);
            if (attrs != null) {
                for (Attr attr : attrs) {
                    if (!isValidParamForParent(parentTag, attr.getLocalName())) {
                        context.report(ISSUE, attr, context.getLocation(attr),
                                "Invalid layout param in a " + parentTag + ": " + attr.getName());
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        mLayoutParams.clear();
    }

    private static boolean isUniversalParam(@NonNull String name) {
        return name.equals("layout_width") || name.equals("layout_height")
                || name.startsWith("layout_margin");
    }

    private static boolean isValidParamForParent(@NonNull String parentTag, @Nullable String paramName) {
        if (paramName == null) {
            return true;
        }
        if (parentTag.endsWith("LinearLayout")) {
            return paramName.equals("layout_gravity") || paramName.equals("layout_weight");
        } else if (parentTag.endsWith("FrameLayout") || parentTag.endsWith("Toolbar")
                || parentTag.endsWith("DrawerLayout")) {
            return paramName.equals("layout_gravity");
        } else if (parentTag.endsWith("RelativeLayout")) {
            return paramName.startsWith("layout_align") || paramName.startsWith("layout_center")
                    || paramName.startsWith("layout_to") || paramName.equals("layout_above")
                    || paramName.equals("layout_below") || paramName.equals("layout_alignWithParentIfMissing");
        } else if (parentTag.endsWith("ConstraintLayout")) {
            return paramName.startsWith("layout_constraint") || paramName.startsWith("layout_goneMargin")
                    || paramName.startsWith("layout_editor_");
        } else if (parentTag.endsWith("GridLayout")) {
            return paramName.startsWith("layout_row") || paramName.startsWith("layout_column")
                    || paramName.equals("layout_gravity");
        } else if (parentTag.endsWith("CoordinatorLayout")) {
            return paramName.startsWith("layout_anchor") || paramName.equals("layout_behavior")
                    || paramName.equals("layout_keyline") || paramName.equals("layout_insetEdge")
                    || paramName.equals("layout_dodgeInsetEdges");
        }
        return true;
    }
}