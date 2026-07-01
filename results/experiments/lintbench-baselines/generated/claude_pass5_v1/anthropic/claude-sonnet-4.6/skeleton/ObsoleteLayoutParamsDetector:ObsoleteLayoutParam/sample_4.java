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
import static com.android.SdkConstants.FRAME_LAYOUT;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.GRID_VIEW;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TABLE_LAYOUT;
import static com.android.SdkConstants.TABLE_ROW;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    // Attributes that are specific to certain parent layouts
    // Maps attribute name -> set of parent layout types that support it
    private static final Map<String, Set<String>> PARAM_TO_PARENTS;

    // Attributes that are valid for any ViewGroup (all layouts support these)
    private static final Set<String> COMMON_PARAMS =
            new HashSet<>(
                    Arrays.asList(
                            "layout_width",
                            "layout_height",
                            "layout_margin",
                            "layout_marginLeft",
                            "layout_marginTop",
                            "layout_marginRight",
                            "layout_marginBottom",
                            "layout_marginStart",
                            "layout_marginEnd",
                            "layout_marginHorizontal",
                            "layout_marginVertical"));

    static {
        PARAM_TO_PARENTS = new HashMap<>();

        // LinearLayout specific
        Set<String> linearLayouts =
                new HashSet<>(Arrays.asList(LINEAR_LAYOUT, TABLE_LAYOUT, TABLE_ROW, GRID_LAYOUT));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_WEIGHT, new HashSet<>(Arrays.asList(LINEAR_LAYOUT, TABLE_ROW)));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_GRAVITY, linearLayouts);

        // RelativeLayout specific
        Set<String> relativeLayouts = new HashSet<>(Collections.singletonList(RELATIVE_LAYOUT));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ABOVE, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_BELOW, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_TO_LEFT_OF, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_TO_RIGHT_OF, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_TO_START_OF, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_TO_END_OF, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_LEFT, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_RIGHT, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_START, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_END, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_TOP, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_BOTTOM, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_BASELINE, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_LEFT, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_RIGHT, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_START, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_END, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_TOP, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_CENTER_HORIZONTAL, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_CENTER_VERTICAL, relativeLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_CENTER_IN_PARENT, relativeLayouts);

        // TableLayout/TableRow specific
        Set<String> tableLayouts = new HashSet<>(Arrays.asList(TABLE_LAYOUT, TABLE_ROW));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_COLUMN, tableLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_SPAN, tableLayouts);

        // GridLayout specific
        Set<String> gridLayouts = new HashSet<>(Collections.singletonList(GRID_LAYOUT));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ROW, gridLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ROW_SPAN, gridLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_COLUMN_SPAN, gridLayouts);

        // AbsoluteLayout specific
        Set<String> absoluteLayouts = new HashSet<>(Collections.singletonList("AbsoluteLayout"));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_X, absoluteLayouts);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_Y, absoluteLayouts);
    }

    // The set of layout attribute local names we want to check
    private static final Set<String> LAYOUT_ATTRS_TO_CHECK = PARAM_TO_PARENTS.keySet();

    // Elements that can contain children (layouts)
    private static final Set<String> LAYOUT_ELEMENTS =
            new HashSet<>(
                    Arrays.asList(
                            LINEAR_LAYOUT,
                            RELATIVE_LAYOUT,
                            FRAME_LAYOUT,
                            TABLE_LAYOUT,
                            TABLE_ROW,
                            GRID_LAYOUT,
                            GRID_VIEW,
                            "AbsoluteLayout",
                            "RadioGroup",
                            "ScrollView",
                            "HorizontalScrollView",
                            "ViewGroup",
                            "merge",
                            "include",
                            "ConstraintLayout",
                            "android.support.constraint.ConstraintLayout",
                            "androidx.constraintlayout.widget.ConstraintLayout",
                            "CoordinatorLayout",
                            "android.support.design.widget.CoordinatorLayout",
                            "androidx.coordinatorlayout.widget.CoordinatorLayout"));

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
        String namespaceUri = attribute.getNamespaceURI();
        if (!ANDROID_URI.equals(namespaceUri)) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        // Skip common params valid for all ViewGroups
        if (COMMON_PARAMS.contains(name)) {
            return;
        }

        // Check if this is one of the params we track
        if (!LAYOUT_ATTRS_TO_CHECK.contains(name)) {
            return;
        }

        // Get the parent element of the element that has this attribute
        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();

        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // No parent element (root element), these layout params have no effect
            // but we can't determine parent layout type
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = getBaseName(parentElement.getTagName());

        // Get the set of parent layouts that support this attribute
        Set<String> validParents = PARAM_TO_PARENTS.get(name);
        if (validParents == null) {
            return;
        }

        // Check if the parent layout supports this attribute
        boolean isValid = false;
        for (String validParent : validParents) {
            String baseValidParent = getBaseName(validParent);
            if (baseValidParent.equals(parentTag)
                    || parentTag.endsWith("." + baseValidParent)
                    || baseValidParent.endsWith("." + parentTag)) {
                isValid = true;
                break;
            }
            // Also check full name match
            if (validParent.equals(parentElement.getTagName())) {
                isValid = true;
                break;
            }
        }

        if (!isValid) {
            // Special case: if parent is "merge", we can't determine the actual parent
            if ("merge".equals(parentTag)) {
                return;
            }

            // Special case: custom views might extend supported layouts
            // Only report for known layout types
            if (!isKnownLayout(parentTag)) {
                return;
            }

            String message =
                    String.format(
                            "Invalid layout param `%1$s` (not defined for `%2$s`)",
                            name, parentTag);

            LintFix fix = LintFix.create().unset(ANDROID_URI, name).build();

            context.report(ISSUE, attribute, context.getLocation(attribute), message, fix);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We handle everything in visitAttribute
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing to do after checking root project
    }

    /**
     * Returns the simple/base name of a layout tag (strips package prefix).
     */
    @NonNull
    private static String getBaseName(@NonNull String tagName) {
        int lastDot = tagName.lastIndexOf('.');
        if (lastDot >= 0) {
            return tagName.substring(lastDot + 1);
        }
        return tagName;
    }

    /**
     * Returns true if the given tag name is a known/standard Android layout type.
     */
    private static boolean isKnownLayout(@NonNull String tagName) {
        switch (tagName) {
            case LINEAR_LAYOUT:
            case RELATIVE_LAYOUT:
            case FRAME_LAYOUT:
            case TABLE_LAYOUT:
            case TABLE_ROW:
            case GRID_LAYOUT:
            case GRID_VIEW:
            case "AbsoluteLayout":
            case "RadioGroup":
            case "ScrollView":
            case "HorizontalScrollView":
            case "ListView":
            case "ExpandableListView":
            case "ViewGroup":
                return true;
            default:
                // Also handle fully-qualified names for common layouts
                return tagName.startsWith("android.widget.")
                        || tagName.startsWith("android.view.")
                        || tagName.startsWith("android.support.")
                        || tagName.startsWith("androidx.");
        }
    }
}