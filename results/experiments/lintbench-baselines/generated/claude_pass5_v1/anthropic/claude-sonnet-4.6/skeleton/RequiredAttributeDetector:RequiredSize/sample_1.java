package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends LayoutDetector {

    private static final String LAYOUT_INFLATER_INFLATE = "inflate";
    private static final String ATTR_LAYOUT_RESOURCE_PREFIX = "layout_";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RequiredAttributeDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` "
                            + "attribute. There is a runtime check for this, so if you fail to "
                            + "specify a size, an exception is thrown at runtime.\n"
                            + "\n"
                            + "It's possible to specify these widths via styles as well. "
                            + "GridLayout, as a special case, does not require you to specify "
                            + "a size.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /**
     * Set of style names that provide layout_width and/or layout_height. Maps style name to a set
     * of attributes provided by that style.
     */
    private final Map<String, Set<String>> mStyleAttributes = new HashMap<>();

    /**
     * Set of layouts that are included with a parent that already specifies layout params,
     * so we don't need to flag the root element of those layouts.
     */
    private final Set<String> mAttachedToRoot = new HashSet<>();

    /**
     * Set of elements (by id) that are known to have layout_width set via style.
     */
    private final Set<Element> mElementsWithWidthFromStyle = new HashSet<>();

    /**
     * Set of elements (by id) that are known to have layout_height set via style.
     */
    private final Set<Element> mElementsWithHeightFromStyle = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing special needed here; all checks are done in visitElement
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We only care about layout files
        if (!context.getResourceFolderType().equals(ResourceFolderType.LAYOUT)) {
            return;
        }

        String tag = element.getTagName();

        // The root element of a layout doesn't need layout params if it's <merge>
        // GridLayout also doesn't require layout_width/layout_height
        if (tag.equals(VIEW_MERGE)) {
            return;
        }

        // <include> tags handle layout params differently
        if (tag.equals(VIEW_INCLUDE)) {
            return;
        }

        // GridLayout doesn't require layout_width/layout_height
        if (tag.equals(GRID_LAYOUT) || tag.endsWith(".GridLayout")) {
            return;
        }

        // Check if this is the root element
        boolean isRoot = element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                || element.getParentNode() instanceof org.w3c.dom.Document;

        // Root elements don't need layout params (they're set by the inflater/parent)
        // unless they're used as a direct child of another view
        if (isRoot) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if the style provides the missing attributes
        String style = element.getAttribute(ATTR_STYLE);
        if (style != null && !style.isEmpty()) {
            // We can't easily resolve styles at this point in a simple detector,
            // but we note the element for potential style-based resolution
            // For now, if a style is specified, we give benefit of the doubt
            // In a full implementation, we'd resolve the style hierarchy
            Set<String> styleAttrs = resolveStyleAttributes(style);
            if (!hasWidth && styleAttrs.contains(ATTR_LAYOUT_WIDTH)) {
                hasWidth = true;
            }
            if (!hasHeight && styleAttrs.contains(ATTR_LAYOUT_HEIGHT)) {
                hasHeight = true;
            }
        }

        if (!hasWidth && !hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "The required `layout_width` and `layout_height` attributes "
                            + "are missing");
        } else if (!hasWidth) {
            context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "The required `layout_width` attribute is missing");
        } else if (!hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "The required `layout_height` attribute is missing");
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(LAYOUT_INFLATER_INFLATE);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // When a layout is inflated with attachToRoot=false or with a null parent,
        // the root element's layout params are ignored. We could track these cases
        // but for the current implementation we focus on XML-level checks.

        // Check if this is a LayoutInflater.inflate() call
        if (!context.getEvaluator().isMemberInClass(method, "android.view.LayoutInflater")) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() >= 3) {
            // inflate(resource, root, attachToRoot)
            // If root is non-null and attachToRoot is true, the layout params from
            // the XML root element are used. Otherwise they may be ignored.
            // We could add more sophisticated tracking here.
        }
    }

    /**
     * Attempts to resolve which layout attributes are provided by a given style reference.
     * In a real implementation this would traverse the style hierarchy in resource files.
     * Here we return an empty set as a conservative default.
     */
    @NonNull
    private Set<String> resolveStyleAttributes(@NonNull String styleRef) {
        Set<String> attrs = mStyleAttributes.get(styleRef);
        if (attrs != null) {
            return attrs;
        }
        return Collections.emptySet();
    }
}