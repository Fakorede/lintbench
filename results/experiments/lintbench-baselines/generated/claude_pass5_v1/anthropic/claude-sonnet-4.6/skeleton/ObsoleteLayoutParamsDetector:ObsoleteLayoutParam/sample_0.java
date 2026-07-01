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
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Attributes that are only valid in specific parent layouts
    // Maps attribute name -> set of valid parent layout types
    private static final Map<String, Set<String>> PARAM_TO_VALID_PARENTS;

    static {
        PARAM_TO_VALID_PARENTS = new HashMap<>();

        // RelativeLayout params
        Set<String> relativeOnly = new HashSet<>(Arrays.asList(RELATIVE_LAYOUT));
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ABOVE, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_BELOW, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_TO_LEFT_OF, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_TO_RIGHT_OF, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_TO_START_OF, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_TO_END_OF, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_TOP, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_BOTTOM, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_LEFT, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_RIGHT, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_START, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_END, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_BASELINE, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_TOP, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_LEFT, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_RIGHT, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_START, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_END, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_CENTER_IN_PARENT, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_CENTER_HORIZONTAL, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_CENTER_VERTICAL, relativeOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING, relativeOnly);

        // LinearLayout params
        Set<String> linearOnly = new HashSet<>(Arrays.asList(LINEAR_LAYOUT, TABLE_LAYOUT, TABLE_ROW));
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_WEIGHT, linearOnly);

        // GridLayout params
        Set<String> gridLayoutOnly = new HashSet<>(Arrays.asList(GRID_LAYOUT));
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ROW, gridLayoutOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_ROW_SPAN, gridLayoutOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_COLUMN, new HashSet<>(Arrays.asList(GRID_LAYOUT, GRID_VIEW)));
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_COLUMN_SPAN, gridLayoutOnly);

        // TableRow / TableLayout params
        Set<String> tableRowOnly = new HashSet<>(Arrays.asList(TABLE_ROW));
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_SPAN, tableRowOnly);

        // AbsoluteLayout params
        Set<String> absoluteOnly = new HashSet<>(Arrays.asList("AbsoluteLayout"));
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_X, absoluteOnly);
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_Y, absoluteOnly);

        // layout_gravity is valid in LinearLayout, FrameLayout, and similar
        Set<String> gravityParents = new HashSet<>(Arrays.asList(
                LINEAR_LAYOUT, FRAME_LAYOUT, TABLE_LAYOUT, TABLE_ROW, GRID_LAYOUT,
                "ScrollView", "HorizontalScrollView"));
        PARAM_TO_VALID_PARENTS.put(ATTR_LAYOUT_GRAVITY, gravityParents);
    }

    // All layout_* attribute local names that we want to check
    private static final Set<String> APPLICABLE_ATTRIBUTES = new HashSet<>(PARAM_TO_VALID_PARENTS.keySet());

    // All layout types that are parents we care about
    private static final Set<String> APPLICABLE_ELEMENTS;

    static {
        APPLICABLE_ELEMENTS = new HashSet<>();
        for (Set<String> parents : PARAM_TO_VALID_PARENTS.values()) {
            APPLICABLE_ELEMENTS.addAll(parents);
        }
        // Also include layouts that are NOT in the valid parents set, because
        // we need to visit child elements within them to detect obsolete params.
        // We visit ALL layout elements so we can check children.
        APPLICABLE_ELEMENTS.add(LINEAR_LAYOUT);
        APPLICABLE_ELEMENTS.add(RELATIVE_LAYOUT);
        APPLICABLE_ELEMENTS.add(FRAME_LAYOUT);
        APPLICABLE_ELEMENTS.add(GRID_LAYOUT);
        APPLICABLE_ELEMENTS.add(GRID_VIEW);
        APPLICABLE_ELEMENTS.add(TABLE_LAYOUT);
        APPLICABLE_ELEMENTS.add(TABLE_ROW);
        APPLICABLE_ELEMENTS.add("AbsoluteLayout");
        APPLICABLE_ELEMENTS.add("ScrollView");
        APPLICABLE_ELEMENTS.add("HorizontalScrollView");
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We don't actually use visitElement for detection; we use visitAttribute.
        // Return null so we don't need to implement visitElement logic for parents.
        return null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return APPLICABLE_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            return;
        }

        // Only check android namespace attributes
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Set<String> validParents = PARAM_TO_VALID_PARENTS.get(localName);
        if (validParents == null) {
            return;
        }

        // Get the element that owns this attribute
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Find the parent element
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // No parent element - this is the root element, layout params don't apply
            // For root elements, we skip the check as there's no parent container
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();

        // Extract the simple class name (last segment after dot) for comparison
        String parentSimpleName = getSimpleName(parentTag);

        // Check if any of the valid parents match the actual parent
        boolean valid = false;
        for (String validParent : validParents) {
            String validSimpleName = getSimpleName(validParent);
            if (validSimpleName.equals(parentSimpleName) || validParent.equals(parentTag)) {
                valid = true;
                break;
            }
        }

        if (!valid) {
            // Report the issue
            String message = String.format(
                    "Invalid layout param in a `%1$s`: `%2$s`",
                    parentSimpleName,
                    attribute.getName());

            LintFix fix = LintFix.create()
                    .name("Remove attribute " + attribute.getName())
                    .unset(attribute.getNamespaceURI(), localName)
                    .build();

            context.report(ISSUE, attribute, context.getLocation(attribute), message, fix);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used - detection is done in visitAttribute
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing to finalize
    }

    /**
     * Returns the simple class name (the part after the last dot, if any).
     */
    @NonNull
    private static String getSimpleName(@NonNull String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot >= 0) {
            return qualifiedName.substring(lastDot + 1);
        }
        return qualifiedName;
    }
}