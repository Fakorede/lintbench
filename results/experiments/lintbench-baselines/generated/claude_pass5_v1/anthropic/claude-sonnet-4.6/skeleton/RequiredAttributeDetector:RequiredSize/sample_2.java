package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
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
import com.android.tools.lint.detector.api.LintClient;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
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
import org.w3c.dom.Node;

public class RequiredAttributeDetector extends LayoutDetector {

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

    /** Style names that provide layout_width and/or layout_height */
    private final Map<String, Boolean> mStylesWithWidth = new HashMap<>();
    private final Map<String, Boolean> mStylesWithHeight = new HashMap<>();

    /**
     * Set of elements (by location key) that are missing width or height and need to be
     * checked after we've processed all styles.
     */
    private final List<PendingError> mPendingErrors = new ArrayList<>();

    /** Set of inflated layouts */
    private final Set<String> mInflatedLayouts = new HashSet<>();

    private static class PendingError {
        final XmlContext context;
        final Element element;
        final boolean missingWidth;
        final boolean missingHeight;
        final String style;

        PendingError(
                XmlContext context,
                Element element,
                boolean missingWidth,
                boolean missingHeight,
                String style) {
            this.context = context;
            this.element = element;
            this.missingWidth = missingWidth;
            this.missingHeight = missingHeight;
            this.style = style;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (PendingError pending : mPendingErrors) {
            boolean missingWidth = pending.missingWidth;
            boolean missingHeight = pending.missingHeight;

            if (pending.style != null) {
                String style = pending.style;
                // Strip optional @style/ prefix
                if (style.startsWith("@style/")) {
                    style = style.substring("@style/".length());
                } else if (style.startsWith("@android:style/")) {
                    // Android platform styles - assume they may provide width/height
                    continue;
                }

                if (missingWidth) {
                    Boolean hasWidth = mStylesWithWidth.get(style);
                    if (hasWidth != null && hasWidth) {
                        missingWidth = false;
                    }
                }
                if (missingHeight) {
                    Boolean hasHeight = mStylesWithHeight.get(style);
                    if (hasHeight != null && hasHeight) {
                        missingHeight = false;
                    }
                }
            }

            if (missingWidth || missingHeight) {
                String attr = missingWidth
                        ? (missingHeight ? "layout_width and layout_height" : "layout_width")
                        : "layout_height";
                pending.context.report(
                        ISSUE,
                        pending.element,
                        pending.context.getElementLocation(pending.element),
                        String.format(
                                "The required `%1$s` attribute is missing from `<%2$s>`",
                                attr,
                                pending.element.getTagName()));
            }
        }
        mPendingErrors.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.VALUES) {
            // We're visiting a <style> element - record which styles define width/height
            String tagName = element.getTagName();
            if ("style".equals(tagName)) {
                String styleName = element.getAttribute("name");
                if (styleName != null && !styleName.isEmpty()) {
                    // Check child <item> elements for layout_width and layout_height
                    org.w3c.dom.NodeList children = element.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Node.ELEMENT_NODE) {
                            Element item = (Element) child;
                            if ("item".equals(item.getTagName())) {
                                String name = item.getAttribute("name");
                                if (ATTR_LAYOUT_WIDTH.equals(name)
                                        || (ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_WIDTH).equals(name)) {
                                    mStylesWithWidth.put(styleName, Boolean.TRUE);
                                } else if (ATTR_LAYOUT_HEIGHT.equals(name)
                                        || (ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_HEIGHT).equals(name)) {
                                    mStylesWithHeight.put(styleName, Boolean.TRUE);
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();

        // <merge> and <GridLayout> (and its support library variant) don't need width/height
        if (VIEW_MERGE.equals(tag)) {
            return;
        }

        // GridLayout special case - does not require size
        if (GRID_LAYOUT.equals(tag)
                || tag.equals("android.support.v7.widget.GridLayout")
                || tag.equals("androidx.gridlayout.widget.GridLayout")) {
            return;
        }

        // The root element of a layout doesn't need layout_width/layout_height if it's
        // used as an include or if it's not really a view (e.g. <merge>)
        // Actually, all views need these attributes unless they're in a GridLayout

        // Check if this is the root element
        boolean isRoot = element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                || element.getParentNode() instanceof org.w3c.dom.Document;

        // Check for layout_width and layout_height
        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check parent - if parent is GridLayout, skip
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (GRID_LAYOUT.equals(parentTag)
                    || parentTag.equals("android.support.v7.widget.GridLayout")
                    || parentTag.equals("androidx.gridlayout.widget.GridLayout")) {
                return;
            }
        }

        // Get style attribute which might supply width/height
        String style = element.getAttribute(ATTR_STYLE);
        if (style == null || style.isEmpty()) {
            style = null;
        }

        boolean missingWidth = !hasWidth;
        boolean missingHeight = !hasHeight;

        // If there's a style, defer the check until afterCheckRootProject
        if (style != null) {
            mPendingErrors.add(
                    new PendingError(context, element, missingWidth, missingHeight, style));
        } else {
            String attr = missingWidth
                    ? (missingHeight ? "layout_width and layout_height" : "layout_width")
                    : "layout_height";
            context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    String.format(
                            "The required `%1$s` attribute is missing from `<%2$s>`",
                            attr,
                            tag));
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("inflate");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Track inflate calls to know which layouts are being inflated programmatically
        // This can be used to avoid false positives for root elements of inflated layouts
        List<UExpression> args = node.getValueArguments();
        if (!args.isEmpty()) {
            UExpression firstArg = args.get(0);
            String desc = firstArg.asSourceString();
            if (desc != null) {
                mInflatedLayouts.add(desc);
            }
        }
    }
}