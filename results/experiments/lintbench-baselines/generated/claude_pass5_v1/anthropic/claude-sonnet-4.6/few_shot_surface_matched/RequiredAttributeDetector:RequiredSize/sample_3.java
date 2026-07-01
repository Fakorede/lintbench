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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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

    /** Styles that provide layout_width and/or layout_height */
    private final Map<String, Boolean> mStylesWithWidth = new HashMap<>();
    private final Map<String, Boolean> mStylesWithHeight = new HashMap<>();

    /**
     * Set of elements (by location key) that are missing width or height and need to be
     * reported after we've processed all files (including style files).
     */
    private final List<PendingError> mPendingErrors = new ArrayList<>();

    /** Style names referenced from inflate calls that we should look up */
    private final Set<String> mInflatedStyleNames = new HashSet<>();

    private static final String INFLATE_METHOD = "inflate";
    private static final String SET_CONTENT_VIEW = "setContentView";

    private static class PendingError {
        final XmlContext context;
        final Element element;
        final Location location;
        final String styleName;
        final boolean missingWidth;
        final boolean missingHeight;

        PendingError(
                XmlContext context,
                Element element,
                Location location,
                @Nullable String styleName,
                boolean missingWidth,
                boolean missingHeight) {
            this.context = context;
            this.element = element;
            this.location = location;
            this.styleName = styleName;
            this.missingWidth = missingWidth;
            this.missingHeight = missingHeight;
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
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.VALUES) {
            // We're in a values file — look for style definitions that provide layout dimensions
            if (TAG_STYLE.equals(element.getTagName())) {
                processStyleElement(element);
            }
            return;
        }

        // We're in a layout file
        String tag = element.getTagName();

        // <merge> and <include> don't need layout params at the root
        if (VIEW_MERGE.equals(tag) || VIEW_INCLUDE.equals(tag)) {
            return;
        }

        // GridLayout is exempt
        if (isGridLayout(tag)) {
            return;
        }

        // Root elements don't need layout params (they are set by the parent container)
        // Actually, the Android runtime does require layout_width/layout_height on ALL views
        // including the root — but the root view's params are replaced by the inflater.
        // Per the spec we check all elements.

        // Check if the node is a direct child of a GridLayout — if so, skip
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            String parentTag = ((Element) parentNode).getTagName();
            if (isGridLayout(parentTag)) {
                return;
            }
        }

        // Check for root element of the document
        Document doc = element.getOwnerDocument();
        if (doc != null && doc.getDocumentElement() == element) {
            // Root element: layout params are typically not required
            // but Android does check at runtime, so we still validate.
            // However, for <merge> roots they are not needed. We already handled merge above.
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if a style is applied that might provide these attributes
        String styleName = null;
        if (element.hasAttribute(ATTR_STYLE)) {
            styleName = element.getAttribute(ATTR_STYLE);
            if (styleName != null) {
                styleName = stripStylePrefix(styleName);
            }
        }

        if (styleName != null && !styleName.isEmpty()) {
            // We'll defer to afterCheckRootProject to resolve style references
            mPendingErrors.add(
                    new PendingError(
                            context,
                            element,
                            context.getElementLocation(element),
                            styleName,
                            !hasWidth,
                            !hasHeight));
        } else {
            // No style — report immediately if missing
            reportMissing(context, element, context.getElementLocation(element), !hasWidth, !hasHeight);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        for (PendingError error : mPendingErrors) {
            boolean missingWidth = error.missingWidth;
            boolean missingHeight = error.missingHeight;

            if (error.styleName != null) {
                Boolean providesWidth = mStylesWithWidth.get(error.styleName);
                Boolean providesHeight = mStylesWithHeight.get(error.styleName);

                if (providesWidth != null && providesWidth) {
                    missingWidth = false;
                }
                if (providesHeight != null && providesHeight) {
                    missingHeight = false;
                }
            }

            if (missingWidth || missingHeight) {
                reportMissing(error.context, error.element, error.location, missingWidth, missingHeight);
            }
        }
        mPendingErrors.clear();
    }

    private void reportMissing(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull Location location,
            boolean missingWidth,
            boolean missingHeight) {
        String message;
        if (missingWidth && missingHeight) {
            message =
                    "The required `layout_width` and `layout_height` attributes are missing";
        } else if (missingWidth) {
            message = "The required `layout_width` attribute is missing";
        } else {
            message = "The required `layout_height` attribute is missing";
        }
        context.report(ISSUE, element, location, message);
    }

    private void processStyleElement(@NonNull Element styleElement) {
        String styleName = styleElement.getAttribute(ATTR_NAME);
        if (styleName == null || styleName.isEmpty()) {
            return;
        }

        // Strip any package prefix like "android:" from style name
        styleName = stripStylePrefix(styleName);

        boolean hasWidth = false;
        boolean hasHeight = false;

        // Check if parent style provides width/height
        String parent = styleElement.getAttribute("parent");
        if (parent != null && !parent.isEmpty()) {
            parent = stripStylePrefix(parent);
            Boolean parentWidth = mStylesWithWidth.get(parent);
            Boolean parentHeight = mStylesWithHeight.get(parent);
            if (parentWidth != null && parentWidth) {
                hasWidth = true;
            }
            if (parentHeight != null && parentHeight) {
                hasHeight = true;
            }
        }

        // Also check dot-notation parent (e.g. "MyStyle.Child" inherits from "MyStyle")
        int dotIndex = styleName.lastIndexOf('.');
        if (dotIndex > 0) {
            String dotParent = styleName.substring(0, dotIndex);
            Boolean parentWidth = mStylesWithWidth.get(dotParent);
            Boolean parentHeight = mStylesWithHeight.get(dotParent);
            if (parentWidth != null && parentWidth) {
                hasWidth = true;
            }
            if (parentHeight != null && parentHeight) {
                hasHeight = true;
            }
        }

        NodeList children = styleElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String itemName = item.getAttribute(ATTR_NAME);
                if (ATTR_LAYOUT_WIDTH.equals(itemName)) {
                    hasWidth = true;
                } else if (ATTR_LAYOUT_HEIGHT.equals(itemName)) {
                    hasHeight = true;
                }
            }
        }

        if (hasWidth) {
            mStylesWithWidth.put(styleName, Boolean.TRUE);
        }
        if (hasHeight) {
            mStylesWithHeight.put(styleName, Boolean.TRUE);
        }
    }

    private static boolean isGridLayout(@NonNull String tag) {
        return GRID_LAYOUT.equals(tag)
                || tag.equals("android.widget.GridLayout")
                || tag.endsWith(".GridLayout");
    }

    @NonNull
    private static String stripStylePrefix(@NonNull String style) {
        // Strip @style/ or @android:style/ prefix
        if (style.startsWith("@")) {
            int slashIndex = style.indexOf('/');
            if (slashIndex != -1) {
                return style.substring(slashIndex + 1);
            }
        }
        return style;
    }

    // SourceCodeScanner methods

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(INFLATE_METHOD, SET_CONTENT_VIEW);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // We could track inflated layout resources here, but for this detector
        // the main logic is in the XML scanning. No additional action needed.
    }
}