package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_ABOVE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING;
import static com.android.SdkConstants.ATTR_LAYOUT_BELOW;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_X;
import static com.android.SdkConstants.ATTR_LAYOUT_Y;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TABLE_ROW;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
                    "The given `layout_param` is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Layout params that are specific to RelativeLayout
    private static final Set<String> RELATIVE_LAYOUT_PARAMS =
            new HashSet<>(
                    Arrays.asList(
                            ATTR_LAYOUT_ABOVE,
                            ATTR_LAYOUT_BELOW,
                            ATTR_LAYOUT_TO_LEFT_OF,
                            ATTR_LAYOUT_TO_RIGHT_OF,
                            ATTR_LAYOUT_TO_START_OF,
                            ATTR_LAYOUT_TO_END_OF,
                            ATTR_LAYOUT_ALIGN_LEFT,
                            ATTR_LAYOUT_ALIGN_RIGHT,
                            ATTR_LAYOUT_ALIGN_START,
                            ATTR_LAYOUT_ALIGN_END,
                            ATTR_LAYOUT_ALIGN_TOP,
                            ATTR_LAYOUT_ALIGN_BOTTOM,
                            ATTR_LAYOUT_ALIGN_BASELINE,
                            ATTR_LAYOUT_ALIGN_PARENT_LEFT,
                            ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
                            ATTR_LAYOUT_ALIGN_PARENT_START,
                            ATTR_LAYOUT_ALIGN_PARENT_END,
                            ATTR_LAYOUT_ALIGN_PARENT_TOP,
                            ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
                            ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING,
                            ATTR_LAYOUT_CENTER_HORIZONTAL,
                            ATTR_LAYOUT_CENTER_VERTICAL,
                            ATTR_LAYOUT_CENTER_IN_PARENT));

    // Layout params specific to LinearLayout
    private static final Set<String> LINEAR_LAYOUT_PARAMS =
            new HashSet<>(Arrays.asList(ATTR_LAYOUT_WEIGHT));

    // Layout params specific to GridLayout
    private static final Set<String> GRID_LAYOUT_PARAMS =
            new HashSet<>(
                    Arrays.asList(
                            ATTR_LAYOUT_ROW,
                            ATTR_LAYOUT_ROW_SPAN,
                            ATTR_LAYOUT_COLUMN,
                            ATTR_LAYOUT_COLUMN_SPAN));

    // Layout params specific to TableRow
    private static final Set<String> TABLE_ROW_PARAMS =
            new HashSet<>(Arrays.asList(ATTR_LAYOUT_COLUMN, ATTR_LAYOUT_SPAN));

    // Layout params specific to AbsoluteLayout
    private static final Set<String> ABSOLUTE_LAYOUT_PARAMS =
            new HashSet<>(Arrays.asList(ATTR_LAYOUT_X, ATTR_LAYOUT_Y));

    // All layout params that are layout-specific (not universal)
    private static final Set<String> ALL_SPECIFIC_PARAMS;

    static {
        ALL_SPECIFIC_PARAMS = new HashSet<>();
        ALL_SPECIFIC_PARAMS.addAll(RELATIVE_LAYOUT_PARAMS);
        ALL_SPECIFIC_PARAMS.addAll(LINEAR_LAYOUT_PARAMS);
        ALL_SPECIFIC_PARAMS.addAll(GRID_LAYOUT_PARAMS);
        ALL_SPECIFIC_PARAMS.addAll(TABLE_ROW_PARAMS);
        ALL_SPECIFIC_PARAMS.addAll(ABSOLUTE_LAYOUT_PARAMS);
        // layout_gravity is valid in LinearLayout, FrameLayout, etc. but not RelativeLayout
        ALL_SPECIFIC_PARAMS.add(ATTR_LAYOUT_GRAVITY);
    }

    // The set of all attribute local names we want to check
    private static final Set<String> APPLICABLE_ATTRIBUTES = ALL_SPECIFIC_PARAMS;

    // The set of all parent layout tags we want to visit
    private static final Set<String> APPLICABLE_ELEMENTS =
            new HashSet<>(
                    Arrays.asList(
                            LINEAR_LAYOUT,
                            RELATIVE_LAYOUT,
                            GRID_LAYOUT,
                            TABLE_ROW,
                            "AbsoluteLayout",
                            "android.widget.LinearLayout",
                            "android.widget.RelativeLayout",
                            "android.widget.GridLayout",
                            "android.widget.TableRow",
                            "android.widget.AbsoluteLayout",
                            "FrameLayout",
                            "android.widget.FrameLayout",
                            "TableLayout",
                            "android.widget.TableLayout",
                            "GridView",
                            "android.widget.GridView",
                            "ScrollView",
                            "android.widget.ScrollView",
                            "HorizontalScrollView",
                            "android.widget.HorizontalScrollView"));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList(); // We handle everything via visitAttribute
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return APPLICABLE_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        // Find the parent element (the layout container)
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Root element — no parent layout, so layout params don't apply
            // But root elements can still have layout params for include scenarios;
            // we'll be conservative and not flag root elements.
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();
        if (parentTag == null) {
            return;
        }

        // Determine if this attribute is valid for the given parent
        if (!isValidLayoutParam(name, parentTag)) {
            String message =
                    String.format(
                            "Invalid layout param `%1$s` (parent is `%2$s`)", name, parentTag);
            LintFix fix = LintFix.create().unset(ANDROID_URI, name).build();
            context.report(ISSUE, attribute, context.getLocation(attribute), message, fix);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used; logic is in visitAttribute
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing to do after checking
    }

    /**
     * Returns true if the given layout param attribute name is valid for the given parent layout
     * tag.
     */
    private static boolean isValidLayoutParam(@NonNull String attrName, @NonNull String parentTag) {
        // Normalize the parent tag (strip package prefix if present)
        String tag = parentTag;
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex >= 0) {
            tag = parentTag.substring(dotIndex + 1);
        }

        switch (attrName) {
            // RelativeLayout-specific params
            case ATTR_LAYOUT_ABOVE:
            case ATTR_LAYOUT_BELOW:
            case ATTR_LAYOUT_TO_LEFT_OF:
            case ATTR_LAYOUT_TO_RIGHT_OF:
            case ATTR_LAYOUT_TO_START_OF:
            case ATTR_LAYOUT_TO_END_OF:
            case ATTR_LAYOUT_ALIGN_LEFT:
            case ATTR_LAYOUT_ALIGN_RIGHT:
            case ATTR_LAYOUT_ALIGN_START:
            case ATTR_LAYOUT_ALIGN_END:
            case ATTR_LAYOUT_ALIGN_TOP:
            case ATTR_LAYOUT_ALIGN_BOTTOM:
            case ATTR_LAYOUT_ALIGN_BASELINE:
            case ATTR_LAYOUT_ALIGN_PARENT_LEFT:
            case ATTR_LAYOUT_ALIGN_PARENT_RIGHT:
            case ATTR_LAYOUT_ALIGN_PARENT_START:
            case ATTR_LAYOUT_ALIGN_PARENT_END:
            case ATTR_LAYOUT_ALIGN_PARENT_TOP:
            case ATTR_LAYOUT_ALIGN_PARENT_BOTTOM:
            case ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING:
            case ATTR_LAYOUT_CENTER_HORIZONTAL:
            case ATTR_LAYOUT_CENTER_VERTICAL:
            case ATTR_LAYOUT_CENTER_IN_PARENT:
                return isRelativeLayout(tag);

            // LinearLayout-specific params
            case ATTR_LAYOUT_WEIGHT:
                return isLinearLayout(tag);

            // GridLayout-specific params
            case ATTR_LAYOUT_ROW:
            case ATTR_LAYOUT_ROW_SPAN:
            case ATTR_LAYOUT_COLUMN_SPAN:
                return isGridLayout(tag);

            // layout_column is valid in both GridLayout and TableRow
            case ATTR_LAYOUT_COLUMN:
                return isGridLayout(tag) || isTableRow(tag);

            // layout_span is valid in TableRow
            case ATTR_LAYOUT_SPAN:
                return isTableRow(tag);

            // AbsoluteLayout-specific params
            case ATTR_LAYOUT_X:
            case ATTR_LAYOUT_Y:
                return isAbsoluteLayout(tag);

            // layout_gravity is valid in LinearLayout, FrameLayout, ScrollView,
            // HorizontalScrollView, TableLayout, TableRow, GridLayout, but NOT RelativeLayout
            case ATTR_LAYOUT_GRAVITY:
                return isLinearLayout(tag)
                        || isFrameLayout(tag)
                        || isGridLayout(tag)
                        || isTableRow(tag)
                        || isTableLayout(tag);

            default:
                // Unknown layout param — don't flag it
                return true;
        }
    }

    private static boolean isRelativeLayout(@NonNull String tag) {
        return RELATIVE_LAYOUT.equals(tag) || "RelativeLayout".equals(tag);
    }

    private static boolean isLinearLayout(@NonNull String tag) {
        return LINEAR_LAYOUT.equals(tag)
                || "LinearLayout".equals(tag)
                || "RadioGroup".equals(tag)
                || "TableLayout".equals(tag)
                || TABLE_ROW.equals(tag)
                || "TableRow".equals(tag);
    }

    private static boolean isGridLayout(@NonNull String tag) {
        return GRID_LAYOUT.equals(tag) || "GridLayout".equals(tag);
    }

    private static boolean isTableRow(@NonNull String tag) {
        return TABLE_ROW.equals(tag) || "TableRow".equals(tag);
    }

    private static boolean isTableLayout(@NonNull String tag) {
        return "TableLayout".equals(tag);
    }

    private static boolean isAbsoluteLayout(@NonNull String tag) {
        return "AbsoluteLayout".equals(tag);
    }

    private static boolean isFrameLayout(@NonNull String tag) {
        return "FrameLayout".equals(tag)
                || "ScrollView".equals(tag)
                || "HorizontalScrollView".equals(tag)
                || "ViewAnimator".equals(tag)
                || "ViewFlipper".equals(tag)
                || "ViewSwitcher".equals(tag)
                || "ImageSwitcher".equals(tag)
                || "TextSwitcher".equals(tag);
    }
}