package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
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

public class RequiredAttributeDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

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
                    new Implementation(
                            RequiredAttributeDetector.class,
                            EnumSet.of(Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES)));

    /** Set of style names that define layout_width */
    private final Set<String> mWidthStyles = new HashSet<>();

    /** Set of style names that define layout_height */
    private final Set<String> mHeightStyles = new HashSet<>();

    /**
     * Set of style names that are referenced by a parent that provides
     * layout_width
     */
    private final Set<String> mWidthParentStyles = new HashSet<>();

    /**
     * Set of style names that are referenced by a parent that provides
     * layout_height
     */
    private final Set<String> mHeightParentStyles = new HashSet<>();

    /**
     * Map from style name to parent style name, used to resolve inheritance
     */
    private final Map<String, String> mStyleParents = new HashMap<>();

    /**
     * Pending list of elements that may need to be checked once all style
     * information has been gathered.
     */
    private List<PendingElement> mPendingElements;

    /** Whether we are currently in a values/ resource file (styles) */
    private boolean mInValueFile;

    /** Whether we are currently in a layout file */
    private boolean mInLayoutFile;

    /** Context for the current file */
    private XmlContext mContext;

    private static final String ATTR_PARENT = "parent";

    private static final String[] EXCLUDED_TAGS = {
        VIEW_MERGE,
        VIEW_INCLUDE,
        "requestFocus",
        "fragment",
        "tag",
        "GridLayout",
        GRID_LAYOUT,
        "android.support.v7.widget.GridLayout",
        "androidx.gridlayout.widget.GridLayout",
    };

    private static boolean isExcluded(String tag) {
        for (String excluded : EXCLUDED_TAGS) {
            if (excluded.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.VALUES) {
            // We are in a values file — look for style definitions
            visitValueElement(context, element);
        } else if (folderType == ResourceFolderType.LAYOUT) {
            // We are in a layout file — check for missing layout_width/layout_height
            visitLayoutElement(context, element);
        }
    }

    private void visitValueElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (!TAG_STYLE.equals(tag)) {
            return;
        }

        String styleName = element.getAttribute(ATTR_NAME);
        if (styleName == null || styleName.isEmpty()) {
            return;
        }

        // Record parent style
        String parent = element.getAttribute(ATTR_PARENT);
        if (parent != null && !parent.isEmpty()) {
            // Normalize parent name
            parent = normalizeStyleName(parent);
            mStyleParents.put(styleName, parent);
        } else {
            // Check for implicit parent via dot notation
            int dotIndex = styleName.lastIndexOf('.');
            if (dotIndex > 0) {
                String implicitParent = styleName.substring(0, dotIndex);
                mStyleParents.put(styleName, implicitParent);
            }
        }

        // Check items within this style
        boolean definesWidth = false;
        boolean definesHeight = false;

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String itemName = item.getAttribute(ATTR_NAME);
                if (ATTR_LAYOUT_WIDTH.equals(itemName)
                        || ("android:" + ATTR_LAYOUT_WIDTH).equals(itemName)) {
                    definesWidth = true;
                } else if (ATTR_LAYOUT_HEIGHT.equals(itemName)
                        || ("android:" + ATTR_LAYOUT_HEIGHT).equals(itemName)) {
                    definesHeight = true;
                }
            }
            child = child.getNextSibling();
        }

        if (definesWidth) {
            mWidthStyles.add(styleName);
        }
        if (definesHeight) {
            mHeightStyles.add(styleName);
        }
    }

    private void visitLayoutElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        // Root element or excluded tags don't need layout params
        if (isExcluded(tag)) {
            return;
        }

        // The root element of a layout doesn't need layout_width/height
        // unless it's included into another layout — but we can't know that
        // statically. The Android framework does require it, so we check
        // all elements. However, the root element of a layout file typically
        // doesn't have a parent so we skip it. Actually, Android does require
        // layout_width and layout_height on ALL views including root.
        // We skip root only if it's <merge>.

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if the style attribute provides these
        String styleValue = element.getAttribute(ATTR_STYLE);
        if (styleValue != null && !styleValue.isEmpty()) {
            String styleName = normalizeStyleName(styleValue);
            if (!hasWidth && styleDefinesWidth(styleName)) {
                hasWidth = true;
            }
            if (!hasHeight && styleDefinesHeight(styleName)) {
                hasHeight = true;
            }
        }

        if (hasWidth && hasHeight) {
            return;
        }

        // We might not have processed all styles yet (if they come from
        // a different file). Add to pending list to check after all files
        // are processed.
        if (mPendingElements == null) {
            mPendingElements = new ArrayList<>();
        }
        mPendingElements.add(new PendingElement(context, element, tag, hasWidth, hasHeight));
    }

    private boolean styleDefinesWidth(String styleName) {
        if (mWidthStyles.contains(styleName)) {
            return true;
        }
        String parent = mStyleParents.get(styleName);
        if (parent != null) {
            return styleDefinesWidth(parent);
        }
        return false;
    }

    private boolean styleDefinesHeight(String styleName) {
        if (mHeightStyles.contains(styleName)) {
            return true;
        }
        String parent = mStyleParents.get(styleName);
        if (parent != null) {
            return styleDefinesHeight(parent);
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPendingElements == null) {
            return;
        }

        for (PendingElement pending : mPendingElements) {
            Element element = pending.element;
            boolean hasWidth = pending.hasWidth;
            boolean hasHeight = pending.hasHeight;

            // Re-check with all style info now available
            String styleValue = element.getAttribute(ATTR_STYLE);
            if (styleValue != null && !styleValue.isEmpty()) {
                String styleName = normalizeStyleName(styleValue);
                if (!hasWidth && styleDefinesWidth(styleName)) {
                    hasWidth = true;
                }
                if (!hasHeight && styleDefinesHeight(styleName)) {
                    hasHeight = true;
                }
            }

            if (!hasWidth || !hasHeight) {
                String tag = pending.tag;
                if (isExcluded(tag)) {
                    continue;
                }

                XmlContext xmlContext = pending.context;
                Location location = xmlContext.getNameLocation(element);

                if (!hasWidth && !hasHeight) {
                    xmlContext.report(
                            ISSUE,
                            element,
                            location,
                            "The required `layout_width` and `layout_height` attributes "
                                    + "are missing");
                } else if (!hasWidth) {
                    xmlContext.report(
                            ISSUE,
                            element,
                            location,
                            "The required `layout_width` attribute is missing");
                } else {
                    xmlContext.report(
                            ISSUE,
                            element,
                            location,
                            "The required `layout_height` attribute is missing");
                }
            }
        }

        mPendingElements = null;
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("inflate");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // When inflating with attachToRoot=false or no parent, the inflated view's
        // root layout params may be ignored. We don't flag this here since we
        // already check the XML directly. This method is a hook for future use
        // or for tracking inflate calls that might bypass layout param checks.
    }

    /** Normalizes a style reference to just the style name */
    private static String normalizeStyleName(String style) {
        // Strip @style/ or @android:style/ prefix
        if (style.startsWith("@")) {
            int slash = style.indexOf('/');
            if (slash != -1) {
                style = style.substring(slash + 1);
            }
        }
        // Replace dots and slashes used in some references
        return style;
    }

    /** Holds information about a layout element that needs deferred checking */
    private static class PendingElement {
        final XmlContext context;
        final Element element;
        final String tag;
        boolean hasWidth;
        boolean hasHeight;

        PendingElement(
                XmlContext context,
                Element element,
                String tag,
                boolean hasWidth,
                boolean hasHeight) {
            this.context = context;
            this.element = element;
            this.tag = tag;
            this.hasWidth = hasWidth;
            this.hasHeight = hasHeight;
        }
    }
}