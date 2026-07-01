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
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TABLE_ROW;

import com.android.annotations.NonNull;
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

    // Layout params that are only valid for specific parent layouts
    // Maps from attribute name to the set of parent layouts that support it
    private static final Map<String, Set<String>> PARAM_TO_PARENTS = new HashMap<>();

    // Layout params that are valid for all ViewGroup parents
    private static final Set<String> GENERAL_PARAMS = new HashSet<>();

    static {
        // RelativeLayout params
        Set<String> relativeLayoutParams = new HashSet<>(Arrays.asList(
                RELATIVE_LAYOUT,
                "android.widget.RelativeLayout"
        ));

        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ABOVE, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_BELOW, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_TO_LEFT_OF, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_TO_RIGHT_OF, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_TO_START_OF, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_TO_END_OF, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_LEFT, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_RIGHT, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_START, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_END, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_TOP, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_BOTTOM, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_BASELINE, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_LEFT, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_RIGHT, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_START, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_END, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_TOP, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_CENTER_IN_PARENT, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_CENTER_HORIZONTAL, relativeLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_CENTER_VERTICAL, relativeLayoutParams);

        // LinearLayout params
        Set<String> linearLayoutParams = new HashSet<>(Arrays.asList(
                LINEAR_LAYOUT,
                "android.widget.LinearLayout",
                TABLE_ROW,
                "android.widget.TableRow"
        ));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_WEIGHT, linearLayoutParams);

        // layout_gravity is supported by LinearLayout, FrameLayout, and GridLayout
        Set<String> gravityParents = new HashSet<>(Arrays.asList(
                LINEAR_LAYOUT,
                "android.widget.LinearLayout",
                FRAME_LAYOUT,
                "android.widget.FrameLayout",
                GRID_LAYOUT,
                "android.widget.GridLayout",
                TABLE_ROW,
                "android.widget.TableRow"
        ));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_GRAVITY, gravityParents);

        // TableRow / TableLayout params
        Set<String> tableRowParams = new HashSet<>(Arrays.asList(
                TABLE_ROW,
                "android.widget.TableRow"
        ));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_COLUMN, tableRowParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_SPAN, tableRowParams);

        // GridLayout params
        Set<String> gridLayoutParams = new HashSet<>(Arrays.asList(
                GRID_LAYOUT,
                "android.widget.GridLayout"
        ));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ROW, gridLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_ROW_SPAN, gridLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_COLUMN_SPAN, gridLayoutParams);

        // AbsoluteLayout params
        Set<String> absoluteLayoutParams = new HashSet<>(Arrays.asList(
                "AbsoluteLayout",
                "android.widget.AbsoluteLayout"
        ));
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_X, absoluteLayoutParams);
        PARAM_TO_PARENTS.put(ATTR_LAYOUT_Y, absoluteLayoutParams);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return PARAM_TO_PARENTS.keySet();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        // Only check layout_ attributes in the android namespace
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (!name.startsWith("layout_")) {
            return;
        }

        Set<String> validParents = PARAM_TO_PARENTS.get(name);
        if (validParents == null) {
            return;
        }

        // Find the parent element
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Root element - no parent layout
            // For root elements, layout params have no effect
            reportIssue(context, attribute, name, "(no parent layout)");
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();

        // Check if the parent is a valid layout for this param
        if (!isValidParent(parentTag, validParents)) {
            String parentDescription = getParentDescription(parentTag);
            reportIssue(context, attribute, name, parentDescription);
        }
    }

    private boolean isValidParent(String parentTag, Set<String> validParents) {
        if (validParents.contains(parentTag)) {
            return true;
        }

        // Check simple class name as well
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex >= 0) {
            String simpleName = parentTag.substring(dotIndex + 1);
            if (validParents.contains(simpleName)) {
                return true;
            }
        } else {
            // Check fully qualified names
            for (String parent : validParents) {
                int parentDot = parent.lastIndexOf('.');
                if (parentDot >= 0) {
                    String simpleParent = parent.substring(parentDot + 1);
                    if (simpleParent.equals(parentTag)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private String getParentDescription(String parentTag) {
        // Return the simple class name
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex >= 0) {
            return parentTag.substring(dotIndex + 1);
        }
        return parentTag;
    }

    private void reportIssue(
            @NonNull XmlContext context,
            @NonNull Attr attribute,
            @NonNull String attrName,
            @NonNull String parentDescription) {
        String message =
                String.format(
                        "Invalid layout param in a `%1$s`: `%2$s`",
                        parentDescription,
                        attrName);

        LintFix fix = LintFix.create()
                .name("Remove attribute")
                .set()
                .attribute(attribute.getNamespaceURI(), attrName, null)
                .build();

        context.report(ISSUE, attribute, context.getLocation(attribute), message, fix);
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