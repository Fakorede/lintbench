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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

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
                    5,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Map<String, Set<String>> VALID_PARAMS = new HashMap<>();

    static {
        VALID_PARAMS.put("LinearLayout", new HashSet<>(Arrays.asList("layout_weight", "layout_gravity")));
        VALID_PARAMS.put("FrameLayout", new HashSet<>(Collections.singletonList("layout_gravity")));
        VALID_PARAMS.put("RelativeLayout", new HashSet<>(Arrays.asList(
                "layout_above", "layout_below", "layout_toLeftOf", "layout_toRightOf",
                "layout_toStartOf", "layout_toEndOf", "layout_alignParentTop", "layout_alignParentBottom",
                "layout_alignParentLeft", "layout_alignParentRight", "layout_alignParentStart", "layout_alignParentEnd",
                "layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical",
                "layout_alignTop", "layout_alignBottom", "layout_alignLeft", "layout_alignRight",
                "layout_alignStart", "layout_alignEnd", "layout_alignBaseline", "layout_alignWithParentIfMissing"
        )));
        VALID_PARAMS.put("ConstraintLayout", new HashSet<>(Collections.singletonList("layout_constraint")));
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Detector.ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Detector.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Element traversal is handled by LayoutDetector.
        // We rely on visitAttribute for the actual validation logic.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")
                || name.equals("layout_width") || name.equals("layout_height")) {
            return;
        }

        org.w3c.dom.Node parentNode = attribute.getOwnerElement().getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        if (parentTag.contains(":")) {
            parentTag = parentTag.substring(parentTag.indexOf(':') + 1);
        }

        Set<String> valid = VALID_PARAMS.get(parentTag);
        if (valid == null) {
            return;
        }

        boolean isValid = valid.contains(name);
        if (!isValid && parentTag.equals("ConstraintLayout") && name.startsWith("layout_constraint")) {
            isValid = true;
        }

        if (!isValid) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Invalid layout param `" + name + "` in a `" + parentTag + "`");
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        super.afterCheckRootProject(context);
    }
}