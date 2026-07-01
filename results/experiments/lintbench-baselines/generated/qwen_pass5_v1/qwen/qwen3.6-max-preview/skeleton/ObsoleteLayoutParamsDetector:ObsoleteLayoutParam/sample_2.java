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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

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
                    "The given layout_param is not defined for the given layout, meaning it has no " +
                    "effect. This usually happens when you change the parent layout or move view " +
                    "code around without updating the layout params. This will cause useless " +
                    "attribute processing at runtime, and is misleading for others reading the " +
                    "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Map<String, Set<String>> VALID_PARENTS = new HashMap<>();

    static {
        addValidParent("layout_weight", "LinearLayout");
        addValidParent("layout_gravity", "LinearLayout", "FrameLayout", "GridLayout", "DrawerLayout",
                "CoordinatorLayout", "AppBarLayout", "CollapsingToolbarLayout", "Toolbar");
        String[] relativeAttrs = {
                "layout_alignParentBottom", "layout_alignParentEnd", "layout_alignParentLeft",
                "layout_alignParentRight", "layout_alignParentStart", "layout_alignParentTop",
                "layout_alignBaseline", "layout_alignBottom", "layout_alignEnd", "layout_alignLeft",
                "layout_alignRight", "layout_alignStart", "layout_alignTop", "layout_alignWithParentIfMissing",
                "layout_centerHorizontal", "layout_centerInParent", "layout_centerVertical",
                "layout_toEndOf", "layout_toLeftOf", "layout_toRightOf", "layout_toStartOf",
                "layout_below", "layout_above"
        };
        for (String attr : relativeAttrs) {
            addValidParent(attr, "RelativeLayout");
        }
        addValidParent("layout_column", "GridLayout");
        addValidParent("layout_row", "GridLayout");
        addValidParent("layout_columnSpan", "GridLayout");
        addValidParent("layout_rowSpan", "GridLayout");
    }

    private static void addValidParent(String attr, String... parents) {
        VALID_PARENTS.put(attr, new HashSet<>(Arrays.asList(parents)));
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
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String ns = attribute.getNamespaceURI();
        if (!ANDROID_URI.equals(ns)) {
            return;
        }
        String name = attribute.getLocalName();
        if (!name.startsWith("layout_")) {
            return;
        }
        if (name.equals("layout_width") || name.equals("layout_height")) {
            return;
        }

        Element parent = (Element) attribute.getOwnerElement().getParentNode();
        if (parent == null) {
            return;
        }

        String parentTag = parent.getTagName();
        if ("merge".equalsIgnoreCase(parentTag) || "include".equalsIgnoreCase(parentTag)) {
            return;
        }

        String simpleParentTag = parentTag.contains(":") ? parentTag.substring(parentTag.indexOf(':') + 1) : parentTag;

        Set<String> validParents = VALID_PARENTS.get(name);
        if (validParents == null) {
            return;
        }

        boolean isValid = false;
        for (String vp : validParents) {
            if (simpleParentTag.equals(vp) || simpleParentTag.endsWith("." + vp)) {
                isValid = true;
                break;
            }
        }

        if (!isValid) {
            String message = String.format(
                    "Invalid layout param `%s` in a `%s` container",
                    attribute.getName(), parentTag);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not required for this detector
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Not required for this detector
    }
}