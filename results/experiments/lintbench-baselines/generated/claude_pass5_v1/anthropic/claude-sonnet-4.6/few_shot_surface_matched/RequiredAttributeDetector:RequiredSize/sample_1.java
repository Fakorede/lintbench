package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_LAYOUT;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class RequiredAttributeDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "All views must specify an explicit `layout_width` and `layout_height`"
                            + " attribute. There is a runtime check for this, so if you fail to"
                            + " specify a size, an exception is thrown at runtime.\n"
                            + "\n"
                            + "It's possible to specify these widths via styles as well."
                            + " GridLayout, as a special case, does not require you to specify"
                            + " a size.",
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
     * Set of layout files that have been inflated via a Java inflate call where we could not
     * determine the parent, so we cannot require layout params
     */
    private final Set<String> mInflatedWithoutParent = new HashSet<>();

    /**
     * Set of layout files that are inflated with attachToRoot=false, meaning they may not need
     * layout params from the parent
     */
    private final Set<String> mAttachToRootFalse = new HashSet<>();

    /** Pending locations to report once we have all style information */
    private final List<PendingError> mPendingErrors = new ArrayList<>();

    private static final String ATTR_LAYOUT_WIDTH_ALIAS = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT_ALIAS = "layout_height";

    /** A pending error to be reported after all files have been checked */
    private static class PendingError {
        final XmlContext context;
        final Element element;
        final Location location;
        final String message;
        final String styleName;
        final boolean missingWidth;
        final boolean missingHeight;

        PendingError(
                XmlContext context,
                Element element,
                Location location,
                String styleName,
                boolean missingWidth,
                boolean missingHeight) {
            this.context = context;
            this.element = element;
            this.location = location;
            this.message = null;
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
            // Collect style information
            handleStyleElement(element);
            return;
        }

        // We're in a layout file
        String tag = element.getTagName();

        // GridLayout does not require layout_width or layout_height
        if (tag.equals(GRID_LAYOUT)
                || tag.equals("android.widget.GridLayout")
                || tag.equals("androidx.gridlayout.widget.GridLayout")) {
            return;
        }

        // <merge> and <include> are special
        if (tag.equals(VIEW_MERGE)) {
            return;
        }

        // The root element doesn't need layout params if the layout is used as an include
        // without a parent, but we still check it for safety unless it's a merge
        Node parentNode = element.getParentNode();
        boolean isRoot = parentNode == null || parentNode.getNodeType() == Node.DOCUMENT_NODE;

        if (isRoot && tag.equals(VIEW_MERGE)) {
            return;
        }

        boolean hasWidth =
                element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight =
                element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if the element has a style attribute that might supply these
        String style = element.getAttribute(ATTR_STYLE);
        if (style != null && !style.isEmpty()) {
            // Strip @style/ prefix if present
            String styleName = getStyleName(style);
            if (styleName != null) {
                boolean styleHasWidth = hasWidth || styleProvidesDimension(styleName, true);
                boolean styleHasHeight = hasHeight || styleProvidesDimension(styleName, false);
                if (styleHasWidth && styleHasHeight) {
                    return;
                }
                if (!styleHasWidth || !styleHasHeight) {
                    // We may not have processed all styles yet (they could be in a different file),
                    // so defer this check
                    mPendingErrors.add(
                            new PendingError(
                                    context,
                                    element,
                                    context.getElementLocation(element),
                                    styleName,
                                    !hasWidth && !styleHasWidth,
                                    !hasHeight && !styleHasHeight));
                    return;
                }
            }
        }

        // Report immediately if we know there's no style to check
        if (style == null || style.isEmpty()) {
            reportMissing(context, element, !hasWidth, !hasHeight);
        }
    }

    private void handleStyleElement(@NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_STYLE.equals(tag)) {
            String name = element.getAttribute(ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                // Check child <item> elements for layout_width and layout_height
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element item = (Element) child;
                        String itemName = item.getAttribute(ATTR_NAME);
                        if (ATTR_LAYOUT_WIDTH.equals(itemName)
                                || ATTR_LAYOUT_WIDTH_ALIAS.equals(itemName)) {
                            mStylesWithWidth.put(name, Boolean.TRUE);
                        } else if (ATTR_LAYOUT_HEIGHT.equals(itemName)
                                || ATTR_LAYOUT_HEIGHT_ALIAS.equals(itemName)) {
                            mStylesWithHeight.put(name, Boolean.TRUE);
                        }
                    }
                }
                // Check for parent style
                String parent = element.getAttribute("parent");
                if (parent != null && !parent.isEmpty()) {
                    String parentName = getStyleName(parent);
                    if (parentName != null) {
                        if (!mStylesWithWidth.containsKey(name)
                                && mStylesWithWidth.containsKey(parentName)) {
                            mStylesWithWidth.put(name, Boolean.TRUE);
                        }
                        if (!mStylesWithHeight.containsKey(name)
                                && mStylesWithHeight.containsKey(parentName)) {
                            mStylesWithHeight.put(name, Boolean.TRUE);
                        }
                    }
                }
                // Check for dotted parent notation (e.g., "ParentStyle.ChildStyle")
                int dotIndex = name.lastIndexOf('.');
                if (dotIndex > 0) {
                    String implicitParent = name.substring(0, dotIndex);
                    if (!mStylesWithWidth.containsKey(name)
                            && mStylesWithWidth.containsKey(implicitParent)) {
                        mStylesWithWidth.put(name, Boolean.TRUE);
                    }
                    if (!mStylesWithHeight.containsKey(name)
                            && mStylesWithHeight.containsKey(implicitParent)) {
                        mStylesWithHeight.put(name, Boolean.TRUE);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        // Now that all files have been processed, resolve pending errors
        for (PendingError pending : mPendingErrors) {
            boolean missingWidth = pending.missingWidth;
            boolean missingHeight = pending.missingHeight;

            if (missingWidth && pending.styleName != null) {
                missingWidth = !styleProvidesDimension(pending.styleName, true);
            }
            if (missingHeight && pending.styleName != null) {
                missingHeight = !styleProvidesDimension(pending.styleName, false);
            }

            if (missingWidth || missingHeight) {
                reportMissing(pending.context, pending.element, missingWidth, missingHeight);
            }
        }
        mPendingErrors.clear();
    }

    private boolean styleProvidesDimension(@NonNull String styleName, boolean width) {
        Map<String, Boolean> map = width ? mStylesWithWidth : mStylesWithHeight;
        return map.containsKey(styleName) && Boolean.TRUE.equals(map.get(styleName));
    }

    private void reportMissing(
            @NonNull XmlContext context,
            @NonNull Element element,
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
        context.report(ISSUE, element, context.getElementLocation(element), message);
    }

    @Nullable
    private static String getStyleName(@NonNull String styleRef) {
        if (styleRef.startsWith("@style/")) {
            return styleRef.substring("@style/".length());
        } else if (styleRef.startsWith("@android:style/")) {
            return styleRef.substring("@android:style/".length());
        } else if (!styleRef.startsWith("@") && !styleRef.startsWith("?")) {
            // Could be a direct style name
            return styleRef;
        }
        return null;
    }

    // SourceCodeScanner methods

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("inflate");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Check calls to LayoutInflater.inflate(int resource, ViewGroup root)
        // or LayoutInflater.inflate(int resource, ViewGroup root, boolean attachToRoot)
        // If root is null or attachToRoot is false, the layout may not need layout params
        List<UExpression> args = call.getValueArguments();
        if (args.size() >= 2) {
            UExpression rootArg = args.get(1);
            String rootStr = rootArg.asSourceString();
            if ("null".equals(rootStr)) {
                // inflate(res, null) - the view won't be attached, so layout params may not matter
                // but we still want to track this
                UExpression resArg = args.get(0);
                String resStr = resArg.asSourceString();
                mInflatedWithoutParent.add(resStr);
            }
            if (args.size() >= 3) {
                UExpression attachArg = args.get(2);
                String attachStr = attachArg.asSourceString();
                if ("false".equals(attachStr)) {
                    UExpression resArg = args.get(0);
                    String resStr = resArg.asSourceString();
                    mAttachToRootFalse.add(resStr);
                }
            }
        }
    }
}