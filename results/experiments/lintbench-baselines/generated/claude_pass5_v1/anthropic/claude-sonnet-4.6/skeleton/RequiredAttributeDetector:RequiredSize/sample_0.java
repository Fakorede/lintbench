package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.REQUEST_FOCUS;
import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.TAG_MERGE;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.SdkConstants.VIEW_TAG;

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

import java.io.File;
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
import org.w3c.dom.NodeList;

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

    /**
     * Map from style name to set of attributes defined in that style. This is used to check
     * whether a style provides layout_width and/or layout_height.
     */
    @Nullable
    private Map<String, Set<String>> mStyleAttributes;

    /**
     * Map from style name to parent style name. Used to resolve style inheritance.
     */
    @Nullable
    private Map<String, String> mStyleParents;

    /**
     * Set of elements (by location key) that are missing layout_width or layout_height, pending
     * style resolution.
     */
    @Nullable
    private Map<String, PendingElement> mPendingElements;

    /**
     * Set of inflated layouts found via Java calls to inflate(). These layouts may be root views
     * that don't need layout params.
     */
    @Nullable
    private Set<String> mInflatedLayouts;

    /** Set of style names that have been seen defined in style resources */
    @Nullable
    private Set<String> mStyleNames;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
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
        // Track inflate() calls to identify layouts that are inflated without a parent
        // In such cases, the root view's layout params might not matter
        List<UExpression> args = node.getValueArguments();
        if (args.size() >= 2) {
            // The second argument is the parent view - if it's null, layout params don't matter
            // We track inflated layouts to potentially exclude them from checks
            // For simplicity, we track all inflated layouts
            if (args.size() >= 1) {
                UExpression resourceArg = args.get(0);
                String resourceString = resourceArg.asSourceString();
                if (resourceString != null && resourceString.contains("R.layout.")) {
                    String layoutName = resourceString.substring(
                            resourceString.lastIndexOf('.') + 1);
                    if (mInflatedLayouts == null) {
                        mInflatedLayouts = new HashSet<>();
                    }
                    // Check if parent is null
                    if (args.size() >= 2) {
                        String parentArg = args.get(1).asSourceString();
                        if ("null".equals(parentArg)) {
                            mInflatedLayouts.add(layoutName);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.VALUES) {
            // We're visiting a style definition - record what attributes it defines
            String tagName = element.getTagName();
            if ("style".equals(tagName)) {
                String styleName = element.getAttribute("name");
                if (styleName != null && !styleName.isEmpty()) {
                    if (mStyleAttributes == null) {
                        mStyleAttributes = new HashMap<>();
                    }
                    if (mStyleNames == null) {
                        mStyleNames = new HashSet<>();
                    }
                    mStyleNames.add(styleName);

                    Set<String> attrs = new HashSet<>();
                    NodeList children = element.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Node.ELEMENT_NODE) {
                            Element item = (Element) child;
                            if ("item".equals(item.getTagName())) {
                                String attrName = item.getAttribute("name");
                                if (attrName != null) {
                                    // Normalize: remove android: prefix for comparison
                                    if (attrName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                                        attrName = attrName.substring(ANDROID_NS_NAME_PREFIX.length());
                                    }
                                    attrs.add(attrName);
                                }
                            }
                        }
                    }
                    mStyleAttributes.put(styleName, attrs);

                    // Record parent style
                    String parent = element.getAttribute("parent");
                    if (parent != null && !parent.isEmpty()) {
                        if (mStyleParents == null) {
                            mStyleParents = new HashMap<>();
                        }
                        // Normalize parent name
                        if (parent.startsWith("@style/")) {
                            parent = parent.substring("@style/".length());
                        } else if (parent.startsWith("@android:style/")) {
                            parent = parent.substring("@android:style/".length());
                        }
                        mStyleParents.put(styleName, parent);
                    } else if (styleName.contains(".")) {
                        // Implicit parent via dot notation
                        String implicitParent = styleName.substring(0, styleName.lastIndexOf('.'));
                        if (mStyleParents == null) {
                            mStyleParents = new HashMap<>();
                        }
                        mStyleParents.put(styleName, implicitParent);
                    }
                }
            }
            return;
        }

        // We're in a layout file
        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();

        // Some tags don't need layout dimensions
        if (tag.equals(REQUEST_FOCUS)
                || tag.equals(TAG_MERGE)
                || tag.equals(VIEW_FRAGMENT)) {
            return;
        }

        // GridLayout doesn't require layout_width/layout_height
        if (isGridLayout(tag)) {
            return;
        }

        // Check if this is the root element - root elements in included layouts
        // might not need layout params
        boolean isRoot = element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                || element.getParentNode() instanceof org.w3c.dom.Document
                || element.getOwnerDocument().getDocumentElement() == element;

        if (isRoot) {
            // Root element - still needs layout_width and layout_height in most cases
            // unless it's being inflated with attachToRoot=false and parent=null
            // We'll still report it but with lower confidence
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if there's a style that might provide these attributes
        String style = element.getAttribute(ATTR_STYLE);
        if (style != null && !style.isEmpty()) {
            // Normalize style reference
            if (style.startsWith("@style/")) {
                style = style.substring("@style/".length());
            } else if (style.startsWith("@android:style/")) {
                style = style.substring("@android:style/".length());
            } else if (style.startsWith("?attr/") || style.startsWith("?android:attr/")) {
                // Theme attribute reference - we can't resolve this statically, skip
                return;
            }

            // We need to defer the check until we've processed all style files
            if (mPendingElements == null) {
                mPendingElements = new HashMap<>();
            }

            String key = context.getLocation(element).toString();
            mPendingElements.put(key, new PendingElement(
                    context, element, style, hasWidth, hasHeight));
            return;
        }

        // Report missing attributes
        reportMissing(context, element, hasWidth, hasHeight);
    }

    private boolean isGridLayout(String tag) {
        return GRID_LAYOUT.equals(tag)
                || tag.equals("android.widget.GridLayout")
                || tag.endsWith(".GridLayout");
    }

    private void reportMissing(
            @NonNull XmlContext context,
            @NonNull Element element,
            boolean hasWidth,
            boolean hasHeight) {
        if (!hasWidth && !hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The required `layout_width` and `layout_height` attributes are missing");
        } else if (!hasWidth) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The required `layout_width` attribute is missing");
        } else {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The required `layout_height` attribute is missing");
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPendingElements == null || mPendingElements.isEmpty()) {
            return;
        }

        // Now resolve pending elements against collected styles
        for (PendingElement pending : mPendingElements.values()) {
            boolean hasWidth = pending.hasWidth;
            boolean hasHeight = pending.hasHeight;

            if (!hasWidth || !hasHeight) {
                // Try to resolve style
                String styleName = pending.styleName;
                Set<String> resolvedAttrs = resolveStyleAttributes(styleName, new HashSet<>());

                if (!hasWidth) {
                    hasWidth = resolvedAttrs.contains(ATTR_LAYOUT_WIDTH);
                }
                if (!hasHeight) {
                    hasHeight = resolvedAttrs.contains(ATTR_LAYOUT_HEIGHT);
                }
            }

            if (!hasWidth || !hasHeight) {
                reportMissing(pending.context, pending.element, hasWidth, hasHeight);
            }
        }
    }

    /**
     * Resolves all attributes provided by a style and its parents.
     */
    @NonNull
    private Set<String> resolveStyleAttributes(
            @NonNull String styleName,
            @NonNull Set<String> visited) {
        if (visited.contains(styleName)) {
            return Collections.emptySet();
        }
        visited.add(styleName);

        Set<String> result = new HashSet<>();

        if (mStyleAttributes != null) {
            Set<String> attrs = mStyleAttributes.get(styleName);
            if (attrs != null) {
                result.addAll(attrs);
            }
        }

        // Check parent style
        if (mStyleParents != null) {
            String parent = mStyleParents.get(styleName);
            if (parent != null) {
                result.addAll(resolveStyleAttributes(parent, visited));
            }
        }

        return result;
    }

    /**
     * Holds information about an element whose style needs to be resolved later.
     */
    private static class PendingElement {
        @NonNull final XmlContext context;
        @NonNull final Element element;
        @NonNull final String styleName;
        final boolean hasWidth;
        final boolean hasHeight;

        PendingElement(
                @NonNull XmlContext context,
                @NonNull Element element,
                @NonNull String styleName,
                boolean hasWidth,
                boolean hasHeight) {
            this.context = context;
            this.element = element;
            this.styleName = styleName;
            this.hasWidth = hasWidth;
            this.hasHeight = hasHeight;
        }
    }
}