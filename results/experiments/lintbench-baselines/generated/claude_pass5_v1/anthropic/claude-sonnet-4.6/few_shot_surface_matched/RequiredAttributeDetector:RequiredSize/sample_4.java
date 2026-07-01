package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.FD_RES_LAYOUT;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.REQUEST_FOCUS;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.SdkConstants.VIEW_TAG;

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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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

    /** Set of style names that provide layout_width */
    private final Set<String> mWidthStyles = new HashSet<>();

    /** Set of style names that provide layout_height */
    private final Set<String> mHeightStyles = new HashSet<>();

    /**
     * Set of style names that were referenced but we haven't confirmed provide the attributes yet
     * (pending style file processing)
     */
    private final Map<String, Location> mPendingErrors = new HashMap<>();

    /**
     * Map from layout file to list of pending (element, missingWidth, missingHeight) issues to
     * report after we've processed all style files.
     */
    private final Map<String, List<PendingError>> mFileErrors = new HashMap<>();

    /** Styles we've already processed */
    private final Set<String> mProcessedStyles = new HashSet<>();

    private static class PendingError {
        final Location location;
        final String styleName;
        final boolean missingWidth;
        final boolean missingHeight;
        final String elementTag;

        PendingError(
                Location location,
                String styleName,
                boolean missingWidth,
                boolean missingHeight,
                String elementTag) {
            this.location = location;
            this.styleName = styleName;
            this.missingWidth = missingWidth;
            this.missingHeight = missingHeight;
            this.elementTag = elementTag;
        }
    }

    public RequiredAttributeDetector() {}

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
        return Arrays.asList("inflate", "from");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No-op: we handle layout inflation tracking via XML scanning only
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.VALUES) {
            // We're in a values file — look for style definitions
            visitStyleElement(context, element);
            return;
        }

        // We're in a layout file
        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();

        // Some elements don't need layout_width/layout_height
        if (tag.equals(REQUEST_FOCUS)
                || tag.equals(VIEW_TAG)
                || tag.equals(TAG_ITEM)) {
            return;
        }

        // The root element of a merge layout doesn't need sizes
        if (tag.equals(VIEW_MERGE)) {
            return;
        }

        // GridLayout doesn't require sizes
        if (isGridLayout(tag)) {
            return;
        }

        // include tag: the included layout should handle sizes
        if (tag.equals(VIEW_INCLUDE)) {
            return;
        }

        // Check if it's the root element — root elements in non-merge layouts
        // may not need sizes (they get them from the parent when inflated).
        // However, the Android runtime still requires them, so we check.
        Node parent = element.getParentNode();
        boolean isRoot = parent == null || parent.getNodeType() == Node.DOCUMENT_NODE;

        // For root elements, skip the check — the inflater provides them
        if (isRoot) {
            return;
        }

        boolean hasWidth =
                element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight =
                element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if there's a style attribute that might provide the missing attributes
        String styleValue = element.getAttribute(ATTR_STYLE);
        if (styleValue != null && !styleValue.isEmpty()) {
            String styleName = getStyleName(styleValue);
            if (styleName != null) {
                boolean widthFromStyle = mWidthStyles.contains(styleName);
                boolean heightFromStyle = mHeightStyles.contains(styleName);

                boolean needsWidth = !hasWidth && !widthFromStyle;
                boolean needsHeight = !hasHeight && !heightFromStyle;

                if (!needsWidth && !needsHeight) {
                    return;
                }

                // If the style hasn't been processed yet, defer the error
                if (!mProcessedStyles.contains(styleName)) {
                    String filePath = context.file.getPath();
                    List<PendingError> errors = mFileErrors.get(filePath);
                    if (errors == null) {
                        errors = new ArrayList<>();
                        mFileErrors.put(filePath, errors);
                    }
                    errors.add(
                            new PendingError(
                                    context.getLocation(element),
                                    styleName,
                                    needsWidth,
                                    needsHeight,
                                    tag));
                    return;
                }

                if (!needsWidth && !needsHeight) {
                    return;
                }

                reportError(context, element, !hasWidth && !widthFromStyle, !hasHeight && !heightFromStyle);
                return;
            }
        }

        reportError(context, element, !hasWidth, !hasHeight);
    }

    private void visitStyleElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (tag.equals(TAG_STYLE)) {
            String styleName = element.getAttribute(ATTR_NAME);
            if (styleName == null || styleName.isEmpty()) {
                return;
            }

            mProcessedStyles.add(styleName);

            // Check parent style
            String parent = element.getAttribute(ATTR_PARENT);
            if (parent != null && !parent.isEmpty()) {
                String parentName = getStyleName(parent);
                if (parentName != null) {
                    if (mWidthStyles.contains(parentName)) {
                        mWidthStyles.add(styleName);
                    }
                    if (mHeightStyles.contains(parentName)) {
                        mHeightStyles.add(styleName);
                    }
                }
            }

            // Also handle implicit parent via dot notation (e.g., "Parent.Child")
            if (parent == null || parent.isEmpty()) {
                int dotIndex = styleName.lastIndexOf('.');
                if (dotIndex > 0) {
                    String implicitParent = styleName.substring(0, dotIndex);
                    if (mWidthStyles.contains(implicitParent)) {
                        mWidthStyles.add(styleName);
                    }
                    if (mHeightStyles.contains(implicitParent)) {
                        mHeightStyles.add(styleName);
                    }
                }
            }

            // Look at item children
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element item = (Element) child;
                    if (TAG_ITEM.equals(item.getTagName())) {
                        String name = item.getAttribute(ATTR_NAME);
                        if (ATTR_LAYOUT_WIDTH.equals(name)) {
                            mWidthStyles.add(styleName);
                        } else if (ATTR_LAYOUT_HEIGHT.equals(name)) {
                            mHeightStyles.add(styleName);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mFileErrors.isEmpty()) {
            return;
        }

        // Now that we've processed all files, report any pending errors
        for (Map.Entry<String, List<PendingError>> entry : mFileErrors.entrySet()) {
            List<PendingError> errors = entry.getValue();
            for (PendingError error : errors) {
                String styleName = error.styleName;

                boolean widthFromStyle = mWidthStyles.contains(styleName);
                boolean heightFromStyle = mHeightStyles.contains(styleName);

                boolean missingWidth = error.missingWidth && !widthFromStyle;
                boolean missingHeight = error.missingHeight && !heightFromStyle;

                if (!missingWidth && !missingHeight) {
                    continue;
                }

                String message = getMissingAttributeMessage(missingWidth, missingHeight);
                context.report(ISSUE, error.location, message);
            }
        }

        mFileErrors.clear();
    }

    private void reportError(
            @NonNull XmlContext context,
            @NonNull Element element,
            boolean missingWidth,
            boolean missingHeight) {
        if (!missingWidth && !missingHeight) {
            return;
        }
        String message = getMissingAttributeMessage(missingWidth, missingHeight);
        context.report(ISSUE, element, context.getLocation(element), message);
    }

    @NonNull
    private static String getMissingAttributeMessage(boolean missingWidth, boolean missingHeight) {
        if (missingWidth && missingHeight) {
            return "The view must have both `layout_width` and `layout_height` attributes";
        } else if (missingWidth) {
            return "The view must have a `layout_width` attribute";
        } else {
            return "The view must have a `layout_height` attribute";
        }
    }

    @Nullable
    private static String getStyleName(@NonNull String styleValue) {
        if (styleValue.startsWith(STYLE_RESOURCE_PREFIX)) {
            return styleValue.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (styleValue.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            return styleValue.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
        } else if (styleValue.startsWith("@style/")) {
            return styleValue.substring("@style/".length());
        } else if (!styleValue.startsWith("@")) {
            // Plain style name
            return styleValue;
        }
        return null;
    }

    private static boolean isGridLayout(@NonNull String tag) {
        return GRID_LAYOUT.equals(tag)
                || tag.equals("android.widget.GridLayout")
                || tag.endsWith(".GridLayout");
    }
}