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
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_LAYOUT_X;
import static com.android.SdkConstants.ATTR_LAYOUT_Y;
import static com.android.SdkConstants.FRAME_LAYOUT;
import static com.android.SdkConstants.GRID_LAYOUT;
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
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Attributes that are valid for all layouts (ViewGroup.LayoutParams)
    private static final Set<String> COMMON_ATTRS =
            new HashSet<>(
                    Arrays.asList(
                            ATTR_LAYOUT_WIDTH,
                            ATTR_LAYOUT_HEIGHT,
                            ATTR_LAYOUT_MARGIN,
                            ATTR_LAYOUT_MARGIN_LEFT,
                            ATTR_LAYOUT_MARGIN_TOP,
                            ATTR_LAYOUT_MARGIN_RIGHT,
                            ATTR_LAYOUT_MARGIN_BOTTOM,
                            ATTR_LAYOUT_MARGIN_START,
                            ATTR_LAYOUT_MARGIN_END));

    // Attributes specific to LinearLayout children
    private static final Set<String> LINEAR_LAYOUT_ATTRS =
            new HashSet<>(Arrays.asList(ATTR_LAYOUT_WEIGHT, ATTR_LAYOUT_GRAVITY));

    // Attributes specific to RelativeLayout children
    private static final Set<String> RELATIVE_LAYOUT_ATTRS =
            new HashSet<>(
                    Arrays.asList(
                            ATTR_LAYOUT_ABOVE,
                            ATTR_LAYOUT_BELOW,
                            ATTR_LAYOUT_TO_LEFT_OF,
                            ATTR_LAYOUT_TO_RIGHT_OF,
                            ATTR_LAYOUT_TO_START_OF,
                            ATTR_LAYOUT_TO_END_OF,
                            ATTR_LAYOUT_ALIGN_TOP,
                            ATTR_LAYOUT_ALIGN_BOTTOM,
                            ATTR_LAYOUT_ALIGN_LEFT,
                            ATTR_LAYOUT_ALIGN_RIGHT,
                            ATTR_LAYOUT_ALIGN_START,
                            ATTR_LAYOUT_ALIGN_END,
                            ATTR_LAYOUT_ALIGN_BASELINE,
                            ATTR_LAYOUT_ALIGN_PARENT_TOP,
                            ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
                            ATTR_LAYOUT_ALIGN_PARENT_LEFT,
                            ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
                            ATTR_LAYOUT_ALIGN_PARENT_START,
                            ATTR_LAYOUT_ALIGN_PARENT_END,
                            ATTR_LAYOUT_CENTER_IN_PARENT,
                            ATTR_LAYOUT_CENTER_HORIZONTAL,
                            ATTR_LAYOUT_CENTER_VERTICAL,
                            ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING));

    // Attributes specific to GridLayout children
    private static final Set<String> GRID_LAYOUT_ATTRS =
            new HashSet<>(
                    Arrays.asList(
                            ATTR_LAYOUT_ROW,
                            ATTR_LAYOUT_ROW_SPAN,
                            ATTR_LAYOUT_COLUMN,
                            ATTR_LAYOUT_COLUMN_SPAN,
                            ATTR_LAYOUT_GRAVITY));

    // Attributes specific to TableRow children
    private static final Set<String> TABLE_ROW_ATTRS =
            new HashSet<>(Arrays.asList(ATTR_LAYOUT_COLUMN, ATTR_LAYOUT_SPAN, ATTR_LAYOUT_GRAVITY));

    // Attributes specific to FrameLayout children
    private static final Set<String> FRAME_LAYOUT_ATTRS =
            new HashSet<>(Collections.singletonList(ATTR_LAYOUT_GRAVITY));

    // Attributes specific to AbsoluteLayout children
    private static final Set<String> ABSOLUTE_LAYOUT_ATTRS =
            new HashSet<>(Arrays.asList(ATTR_LAYOUT_X, ATTR_LAYOUT_Y));

    // Map from layout type to the set of valid layout_* attributes for its children
    private static final Map<String, Set<String>> LAYOUT_TO_PARAMS = new HashMap<>();

    static {
        LAYOUT_TO_PARAMS.put(LINEAR_LAYOUT, LINEAR_LAYOUT_ATTRS);
        LAYOUT_TO_PARAMS.put(TABLE_LAYOUT, LINEAR_LAYOUT_ATTRS);
        LAYOUT_TO_PARAMS.put(RELATIVE_LAYOUT, RELATIVE_LAYOUT_ATTRS);
        LAYOUT_TO_PARAMS.put(GRID_LAYOUT, GRID_LAYOUT_ATTRS);
        LAYOUT_TO_PARAMS.put(TABLE_ROW, TABLE_ROW_ATTRS);
        LAYOUT_TO_PARAMS.put(FRAME_LAYOUT, FRAME_LAYOUT_ATTRS);
        // AbsoluteLayout
        LAYOUT_TO_PARAMS.put("AbsoluteLayout", ABSOLUTE_LAYOUT_ATTRS);
    }

    // All layout_* attributes that are layout-specific (not common)
    private static final Set<String> ALL_SPECIFIC_LAYOUT_ATTRS = new HashSet<>();

    static {
        for (Set<String> attrs : LAYOUT_TO_PARAMS.values()) {
            ALL_SPECIFIC_LAYOUT_ATTRS.addAll(attrs);
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL_SPECIFIC_LAYOUT_ATTRS;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        // Only care about android namespace
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        // Common attributes are always valid
        if (COMMON_ATTRS.contains(name)) {
            return;
        }

        // Get the parent element of the element that has this attribute
        Element element = (Element) attribute.getOwnerElement();
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Root element - no parent layout
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();
        // Strip package prefix for comparison
        if (parentTag.contains(".")) {
            parentTag = parentTag.substring(parentTag.lastIndexOf('.') + 1);
        }

        Set<String> validAttrs = LAYOUT_TO_PARAMS.get(parentTag);
        if (validAttrs == null) {
            // Unknown parent layout - we can't be sure, so skip
            return;
        }

        if (!validAttrs.contains(name)) {
            String message =
                    String.format(
                            "Invalid layout param in a `%1$s`: `%2$s`",
                            parentTag, attribute.getName());
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
        // Nothing to do after checking
    }
}