package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.TAG_MERGE;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;

/**
 * Detector for missing layout_width or layout_height attributes on views.
 */
public class RequiredAttributeDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime.\n" +
            "\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special " +
            "case, does not require you to specify a size.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Set of style names (from values files) that define layout_width and/or layout_height.
     * Key: style name, Value: set of attributes defined in that style.
     */
    private Map<String, Set<String>> mStyleToAttributes;

    /**
     * Set of style names that include other styles (via "parent" attribute or dot notation).
     * Key: style name, Value: parent style name.
     */
    private Map<String, String> mStyleParents;

    /**
     * Pending elements that need to be checked after all files are processed
     * (because they reference styles that might be defined in other files).
     * Each entry: [context, element, missingWidth (bool), missingHeight (bool)]
     */
    private List<Object[]> mPendingElements;

    /** Constructor */
    public RequiredAttributeDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // For values files, we scan for style definitions
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            scanValueDocument(context, document);
        }
    }

    private void scanValueDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_STYLE.equals(element.getTagName())) {
                    processStyleElement(element);
                }
            }
        }
    }

    private void processStyleElement(@NonNull Element styleElement) {
        String styleName = styleElement.getAttribute("name");
        if (styleName == null || styleName.isEmpty()) {
            return;
        }

        // Normalize style name (replace dots with underscores for lookup consistency)
        // Actually keep original name but handle parent lookup

        if (mStyleToAttributes == null) {
            mStyleToAttributes = new HashMap<>();
        }
        if (mStyleParents == null) {
            mStyleParents = new HashMap<>();
        }

        Set<String> attrs = new HashSet<>();

        // Check for parent attribute
        String parent = styleElement.getAttribute("parent");
        if (parent != null && !parent.isEmpty()) {
            // Strip @style/ prefix if present
            if (parent.startsWith("@style/")) {
                parent = parent.substring("@style/".length());
            } else if (parent.startsWith("@android:style/")) {
                parent = parent.substring("@android:style/".length());
            }
            mStyleParents.put(styleName, parent);
        } else {
            // Check for implicit parent via dot notation
            int dotIndex = styleName.lastIndexOf('.');
            if (dotIndex != -1) {
                String implicitParent = styleName.substring(0, dotIndex);
                mStyleParents.put(styleName, implicitParent);
            }
        }

        // Scan items in the style
        NodeList items = styleElement.getChildNodes();
        for (int i = 0, n = items.getLength(); i < n; i++) {
            Node item = items.item(i);
            if (item.getNodeType() == Node.ELEMENT_NODE) {
                Element itemElement = (Element) item;
                if ("item".equals(itemElement.getTagName())) {
                    String name = itemElement.getAttribute("name");
                    if (name != null) {
                        // Normalize: strip android: prefix for comparison
                        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                            name = name.substring(ANDROID_NS_NAME_PREFIX.length());
                        }
                        if (ATTR_LAYOUT_WIDTH.equals(name) || ATTR_LAYOUT_HEIGHT.equals(name)) {
                            attrs.add(name);
                        }
                    }
                }
            }
        }

        mStyleToAttributes.put(styleName, attrs);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();

        // The root element doesn't need layout params (unless it's a merge)
        // Actually, even root elements need layout_width/layout_height in most cases.
        // But the root element of a layout file is typically the root view.
        // Views inside <merge> and the <merge> tag itself don't need layout params.
        // <include> tags do need layout params if they override.
        // Let's check: skip merge tags
        if (TAG_MERGE.equals(tag) || VIEW_MERGE.equals(tag)) {
            return;
        }

        // Skip <include> - it can inherit from the included layout
        if (VIEW_INCLUDE.equals(tag)) {
            return;
        }

        // GridLayout doesn't require size attributes
        if (isGridLayout(tag)) {
            return;
        }

        // Check if this element is a direct child of a GridLayout
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (isGridLayout(parentTag)) {
                return;
            }
        }

        // Check if this is the root element - root elements still need layout_width/height
        // unless they are inside a <merge>
        boolean insideMerge = isInsideMerge(element);
        if (insideMerge) {
            return;
        }

        // Check for layout_width and layout_height
        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if a style is applied that might provide these attributes
        String styleName = element.getAttribute(ATTR_STYLE);
        if (styleName != null && !styleName.isEmpty()) {
            // Strip @style/ prefix
            if (styleName.startsWith("@style/")) {
                styleName = styleName.substring("@style/".length());
            } else if (styleName.startsWith("@android:style/")) {
                styleName = styleName.substring("@android:style/".length());
            }

            // We need to defer this check until after all files are processed
            // because the style might be defined in another file
            if (mPendingElements == null) {
                mPendingElements = new ArrayList<>();
            }
            mPendingElements.add(new Object[]{context, element, styleName, !hasWidth, !hasHeight});
            return;
        }

        // No style reference - report missing attributes immediately
        reportMissing(context, element, !hasWidth, !hasHeight);
    }

    private boolean isGridLayout(@NonNull String tag) {
        return GRID_LAYOUT.equals(tag)
                || tag.equals("android.widget.GridLayout")
                || tag.endsWith(".GridLayout");
    }

    private boolean isInsideMerge(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                String tag = ((Element) parent).getTagName();
                if (TAG_MERGE.equals(tag) || VIEW_MERGE.equals(tag)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private void reportMissing(@NonNull XmlContext context, @NonNull Element element,
            boolean missingWidth, boolean missingHeight) {
        if (!missingWidth && !missingHeight) {
            return;
        }

        String message;
        if (missingWidth && missingHeight) {
            message = "The required `layout_width` and `layout_height` attributes are missing";
        } else if (missingWidth) {
            message = "The required `layout_width` attribute is missing";
        } else {
            message = "The required `layout_height` attribute is missing";
        }

        context.report(ISSUE, element, context.getNameLocation(element), message);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPendingElements != null) {
            for (Object[] pending : mPendingElements) {
                XmlContext xmlContext = (XmlContext) pending[0];
                Element element = (Element) pending[1];
                String styleName = (String) pending[2];
                boolean missingWidth = (Boolean) pending[3];
                boolean missingHeight = (Boolean) pending[4];

                // Check if the style (or any of its parents) provides the missing attributes
                if (missingWidth) {
                    missingWidth = !styleDefinesAttribute(styleName, ATTR_LAYOUT_WIDTH);
                }
                if (missingHeight) {
                    missingHeight = !styleDefinesAttribute(styleName, ATTR_LAYOUT_HEIGHT);
                }

                if (missingWidth || missingHeight) {
                    reportMissing(xmlContext, element, missingWidth, missingHeight);
                }
            }
        }
    }

    private boolean styleDefinesAttribute(@NonNull String styleName, @NonNull String attribute) {
        if (mStyleToAttributes == null) {
            return false;
        }

        // Walk up the style hierarchy
        Set<String> visited = new HashSet<>();
        String current = styleName;
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            Set<String> attrs = mStyleToAttributes.get(current);
            if (attrs != null && attrs.contains(attribute)) {
                return true;
            }
            // Check parent
            current = mStyleParents != null ? mStyleParents.get(current) : null;
        }
        return false;
    }
}