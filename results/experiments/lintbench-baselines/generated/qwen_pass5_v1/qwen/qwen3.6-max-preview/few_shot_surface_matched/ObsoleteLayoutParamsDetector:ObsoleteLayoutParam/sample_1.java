package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.Collection;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
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
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Detector.ALL;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Detector.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Element traversal is handled via attribute visits
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        // Universal layout parameters apply to all ViewGroups
        if (name.equals("layout_width") || name.equals("layout_height")
                || name.startsWith("layout_margin") || name.startsWith("layout_padding")) {
            return;
        }

        Element parent = (Element) attribute.getOwnerElement().getParentNode();
        if (parent == null) {
            return;
        }

        String parentTag = parent.getTagName();
        if (!isValidParamForParent(name, parentTag)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Invalid layout param in a " + parentTag + ": " + name);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No global state requires cleanup
    }

    private static boolean isValidParamForParent(@NonNull String paramName, @NonNull String parentTag) {
        if (paramName.startsWith("layout_weight")) {
            return parentTag.contains("LinearLayout");
        }
        if (paramName.startsWith("layout_align") || paramName.startsWith("layout_center")
                || paramName.startsWith("layout_to") || paramName.startsWith("layout_above")
                || paramName.startsWith("layout_below")) {
            return parentTag.contains("RelativeLayout");
        }
        if (paramName.startsWith("layout_constraint")) {
            return parentTag.contains("ConstraintLayout");
        }
        if (paramName.startsWith("layout_column") || paramName.startsWith("layout_row")
                || paramName.startsWith("layout_span")) {
            return parentTag.contains("GridLayout");
        }
        if (paramName.startsWith("layout_gravity")) {
            return parentTag.contains("LinearLayout") || parentTag.contains("FrameLayout");
        }
        // Unknown layout params are ignored to prevent false positives
        return true;
    }
}