package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_LAYOUT;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.REQUEST_FOCUS;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.VIEW_FRAGMENT;
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

public class RequiredAttributeDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

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

    /** Map from style name to parent style name */
    private Map<String, String> mStyleParents;

    /** Map from style name to set of attribute names defined in that style */
    private Map<String, Set<String>> mStyleAttributes;

    /**
     * Set of layout XML files that we've already checked (to avoid double-checking when both
     * layout and layout-land etc. exist)
     */
    private Set<String> mCheckedLayouts;

    /**
     * Map from layout name to list of pending locations to report (we delay reporting until we've
     * seen all styles)
     */
    private Map<String, List<Location>> mPendingErrors;

    /** Set of style names referenced from inflate calls in Java code */
    private Set<String> mInflatedWidgetStyles;

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
            visitValueElement(context, element);
        } else if (folderType == ResourceFolderType.LAYOUT) {
            visitLayoutElement(context, element);
        }
    }

    private void visitValueElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_STYLE.equals(tag)) {
            String styleName = element.getAttribute(ATTR_NAME);
            if (styleName == null || styleName.isEmpty()) {
                return;
            }
            String parent = element.getAttribute(ATTR_PARENT);
            if (parent != null && !parent.isEmpty()) {
                if (mStyleParents == null) {
                    mStyleParents = new HashMap<>();
                }
                mStyleParents.put(styleName, stripStylePrefix(parent));
            } else if (styleName.indexOf('.') != -1) {
                // Implicit parent: "Foo.Bar" has parent "Foo"
                if (mStyleParents == null) {
                    mStyleParents = new HashMap<>();
                }
                String implicitParent = styleName.substring(0, styleName.lastIndexOf('.'));
                mStyleParents.put(styleName, implicitParent);
            }
            // Collect attributes defined in this style
            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element item = (Element) child;
                    if (TAG_ITEM.equals(item.getTagName())) {
                        String name = item.getAttribute(ATTR_NAME);
                        if (name != null && !name.isEmpty()) {
                            // Strip namespace prefix if present
                            if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                                name = name.substring(ANDROID_NS_NAME_PREFIX.length());
                            }
                            if (ATTR_LAYOUT_WIDTH.equals(name)
                                    || ATTR_LAYOUT_HEIGHT.equals(name)) {
                                if (mStyleAttributes == null) {
                                    mStyleAttributes = new HashMap<>();
                                }
                                Set<String> attrs = mStyleAttributes.get(styleName);
                                if (attrs == null) {
                                    attrs = new HashSet<>();
                                    mStyleAttributes.put(styleName, attrs);
                                }
                                attrs.add(name);
                            }
                        }
                    }
                }
            }
        }
    }

    private void visitLayoutElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        // Ignore some special tags
        if (VIEW_MERGE.equals(tag)
                || VIEW_INCLUDE.equals(tag)
                || REQUEST_FOCUS.equals(tag)
                || TAG_ITEM.equals(tag)
                || VIEW_FRAGMENT.equals(tag)) {
            return;
        }

        // The root element doesn't need layout_width/layout_height in some cases
        // But the spec says ALL views need it, so we check root too.
        // However, <merge> root is already excluded above.

        // GridLayout does not require size attributes
        if (GRID_LAYOUT.equals(tag)
                || tag.endsWith(".GridLayout")) {
            return;
        }

        // Also skip <view> tag used as a placeholder
        if (VIEW_TAG.equals(tag)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if a style is specified that might provide the missing attributes
        String style = element.getAttribute(ATTR_STYLE);
        if (style != null && !style.isEmpty()) {
            String styleName = stripStylePrefix(style);
            if (!hasWidth && styleDefinesAttribute(styleName, ATTR_LAYOUT_WIDTH)) {
                hasWidth = true;
            }
            if (!hasHeight && styleDefinesAttribute(styleName, ATTR_LAYOUT_HEIGHT)) {
                hasHeight = true;
            }
            if (hasWidth && hasHeight) {
                return;
            }
            // We might not have seen all styles yet (values files processed after layout)
            // Defer the check
            if (!hasWidth || !hasHeight) {
                // Store pending error to check after all files are processed
                String layoutName = context.file.getName();
                if (layoutName.endsWith(DOT_XML)) {
                    layoutName = layoutName.substring(0, layoutName.length() - DOT_XML.length());
                }
                String key = context.file.getPath() + ":" + getElementLine(element);
                if (mPendingErrors == null) {
                    mPendingErrors = new HashMap<>();
                }
                List<Location> locations = mPendingErrors.get(key);
                if (locations == null) {
                    locations = new ArrayList<>();
                    mPendingErrors.put(key, locations);
                }
                // Store: location, hasWidth, hasHeight, styleName
                // We'll encode this as a special location with message
                Location location = context.getElementLocation(element);
                // Store metadata in a wrapper
                PendingError pending =
                        new PendingError(
                                location,
                                hasWidth,
                                hasHeight,
                                styleName,
                                context,
                                element);
                mPendingErrors.put(key, Collections.singletonList(location));
                // Actually store the pending error differently
                storePendingError(context, element, hasWidth, hasHeight, styleName);
                return;
            }
        }

        if (!hasWidth || !hasHeight) {
            reportMissing(context, element, hasWidth, hasHeight);
        }
    }

    /** Pending error information */
    private static class PendingError {
        final Location location;
        final boolean hasWidth;
        final boolean hasHeight;
        final String styleName;
        final XmlContext context;
        final Element element;

        PendingError(
                Location location,
                boolean hasWidth,
                boolean hasHeight,
                String styleName,
                XmlContext context,
                Element element) {
            this.location = location;
            this.hasWidth = hasWidth;
            this.hasHeight = hasHeight;
            this.styleName = styleName;
            this.context = context;
            this.element = element;
        }
    }

    private List<PendingError> mPendingErrorList;

    private void storePendingError(
            XmlContext context,
            Element element,
            boolean hasWidth,
            boolean hasHeight,
            String styleName) {
        if (mPendingErrorList == null) {
            mPendingErrorList = new ArrayList<>();
        }
        Location location = context.getElementLocation(element);
        mPendingErrorList.add(
                new PendingError(location, hasWidth, hasHeight, styleName, context, element));
    }

    private void reportMissing(
            XmlContext context, Element element, boolean hasWidth, boolean hasHeight) {
        String message;
        if (!hasWidth && !hasHeight) {
            message =
                    "The required `layout_width` and `layout_height` attributes are missing";
        } else if (!hasWidth) {
            message = "The required `layout_width` attribute is missing";
        } else {
            message = "The required `layout_height` attribute is missing";
        }
        context.report(ISSUE, element, context.getElementLocation(element), message);
    }

    private boolean styleDefinesAttribute(String styleName, String attribute) {
        if (styleName == null || styleName.isEmpty()) {
            return false;
        }
        // Walk up the style hierarchy
        int depth = 0;
        String current = styleName;
        while (current != null && depth < 30) {
            if (mStyleAttributes != null) {
                Set<String> attrs = mStyleAttributes.get(current);
                if (attrs != null && attrs.contains(attribute)) {
                    return true;
                }
            }
            // Move to parent
            if (mStyleParents != null) {
                current = mStyleParents.get(current);
            } else {
                break;
            }
            depth++;
        }
        return false;
    }

    private static String stripStylePrefix(@NonNull String style) {
        if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
            return style.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            return style.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
        }
        return style;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPendingErrorList != null) {
            for (PendingError pending : mPendingErrorList) {
                boolean hasWidth = pending.hasWidth;
                boolean hasHeight = pending.hasHeight;
                String styleName = pending.styleName;

                if (!hasWidth && styleDefinesAttribute(styleName, ATTR_LAYOUT_WIDTH)) {
                    hasWidth = true;
                }
                if (!hasHeight && styleDefinesAttribute(styleName, ATTR_LAYOUT_HEIGHT)) {
                    hasHeight = true;
                }

                // Also check if this style was referenced from an inflate call
                if (mInflatedWidgetStyles != null && mInflatedWidgetStyles.contains(styleName)) {
                    // Assume the inflated style provides the missing attributes
                    continue;
                }

                if (!hasWidth || !hasHeight) {
                    String message;
                    if (!hasWidth && !hasHeight) {
                        message =
                                "The required `layout_width` and `layout_height` attributes "
                                        + "are missing";
                    } else if (!hasWidth) {
                        message = "The required `layout_width` attribute is missing";
                    } else {
                        message = "The required `layout_height` attribute is missing";
                    }
                    pending.context.report(ISSUE, pending.location, message);
                }
            }
            mPendingErrorList = null;
        }

        // Reset state
        mStyleParents = null;
        mStyleAttributes = null;
        mCheckedLayouts = null;
        mPendingErrors = null;
        mInflatedWidgetStyles = null;
    }

    // ---- SourceCodeScanner ----

    private static final String INFLATE_METHOD = "inflate";
    private static final String SET_CONTENT_VIEW = "setContentView";

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return List.of(INFLATE_METHOD, SET_CONTENT_VIEW);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Look for LayoutInflater.inflate(R.layout.foo, parent, false) style calls
        // where a style resource is passed — this is less common but we track
        // inflate calls that pass a style to mark those styles as "used with inflate"
        List<UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        // We look for inflate calls that reference a style (not typical, but handle gracefully)
        // Primarily this method is here to satisfy the interface requirement and to handle
        // any future tracking of inflated styles.
        for (UExpression arg : args) {
            String argText = arg.asSourceString();
            if (argText != null
                    && (argText.contains("R.style.") || argText.contains("android.R.style."))) {
                String styleName = extractStyleName(argText);
                if (styleName != null) {
                    if (mInflatedWidgetStyles == null) {
                        mInflatedWidgetStyles = new HashSet<>();
                    }
                    mInflatedWidgetStyles.add(styleName);
                }
            }
        }
    }

    @Nullable
    private static String extractStyleName(@NonNull String expression) {
        int dotIndex = expression.lastIndexOf('.');
        if (dotIndex != -1 && dotIndex < expression.length() - 1) {
            return expression.substring(dotIndex + 1);
        }
        return null;
    }

    private static int getElementLine(Element element) {
        try {
            Object userData = element.getUserData("lineNumber");
            if (userData instanceof Integer) {
                return (Integer) userData;
            }
        } catch (Exception ignore) {
            // ignore
        }
        return -1;
    }
}