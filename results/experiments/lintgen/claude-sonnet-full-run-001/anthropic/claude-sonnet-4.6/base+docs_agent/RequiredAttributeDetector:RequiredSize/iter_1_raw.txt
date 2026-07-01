package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
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
     * Set of style names that define layout_width and/or layout_height.
     * Key: style name, Value: set of attributes defined in that style.
     */
    private Map<String, Set<String>> mStyleToAttributes;

    /**
     * Set of style names that include other styles (via parent or item references).
     * Key: style name, Value: parent style name.
     */
    private Map<String, String> mStyleParents;

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
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            // Process style definitions
            processStyleDocument(context, document);
        } else if (folderType == ResourceFolderType.LAYOUT) {
            // Process layout file
            processLayoutDocument(context, document);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We handle everything in visitDocument
    }

    private void processStyleDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (mStyleToAttributes == null) {
            mStyleToAttributes = new HashMap<>();
            mStyleParents = new HashMap<>();
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element styleElement = (Element) child;
            if (!"style".equals(styleElement.getTagName())) {
                continue;
            }

            String styleName = styleElement.getAttribute("name");
            if (styleName == null || styleName.isEmpty()) {
                continue;
            }

            // Normalize style name
            styleName = normalizeStyleRef(styleName);

            // Check for parent attribute
            String parent = styleElement.getAttribute("parent");
            if (parent != null && !parent.isEmpty()) {
                mStyleParents.put(styleName, normalizeStyleRef(parent));
            } else if (styleName.contains(".")) {
                // Implicit parent via dot notation
                int lastDot = styleName.lastIndexOf('.');
                mStyleParents.put(styleName, styleName.substring(0, lastDot));
            }

            // Collect layout_width and layout_height items
            Set<String> attrs = new HashSet<>();
            NodeList items = styleElement.getChildNodes();
            for (int j = 0, m = items.getLength(); j < m; j++) {
                Node item = items.item(j);
                if (item.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element itemElement = (Element) item;
                if (!"item".equals(itemElement.getTagName())) {
                    continue;
                }
                String name = itemElement.getAttribute("name");
                if (name == null) {
                    continue;
                }
                // Strip android: prefix if present
                if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                    name = name.substring(ANDROID_NS_NAME_PREFIX.length());
                }
                if (ATTR_LAYOUT_WIDTH.equals(name) || ATTR_LAYOUT_HEIGHT.equals(name)) {
                    attrs.add(name);
                }
            }

            if (!attrs.isEmpty()) {
                mStyleToAttributes.put(styleName, attrs);
            }
        }
    }

    private void processLayoutDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        checkElement(context, root, true);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element,
            boolean isRoot) {
        String tag = element.getTagName();

        // merge root elements don't need layout params themselves,
        // but their children do
        if (VIEW_MERGE.equals(tag)) {
            checkChildren(context, element);
            return;
        }

        // GridLayout doesn't require layout_width/layout_height
        if (isGridLayout(tag)) {
            checkChildren(context, element);
            return;
        }

        // Check if this element has layout_width and layout_height
        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            // Check if style provides the missing attributes
            String style = element.getAttribute(ATTR_STYLE);
            if (style != null && !style.isEmpty()) {
                Set<String> styleAttrs = getAttributesFromStyle(style);
                if (styleAttrs != null) {
                    if (styleAttrs.contains(ATTR_LAYOUT_WIDTH)) {
                        hasWidth = true;
                    }
                    if (styleAttrs.contains(ATTR_LAYOUT_HEIGHT)) {
                        hasHeight = true;
                    }
                }
            }
        }

        if (!hasWidth || !hasHeight) {
            // Report the issue
            if (!hasWidth && !hasHeight) {
                context.report(ISSUE, element, context.getElementLocation(element),
                        "The required `layout_width` and `layout_height` attributes are missing");
            } else if (!hasWidth) {
                context.report(ISSUE, element, context.getElementLocation(element),
                        "The required `layout_width` attribute is missing");
            } else {
                context.report(ISSUE, element, context.getElementLocation(element),
                        "The required `layout_height` attribute is missing");
            }
        }

        checkChildren(context, element);
    }

    private void checkChildren(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, false);
            }
        }
    }

    private boolean isGridLayout(@NonNull String tag) {
        return GRID_LAYOUT.equals(tag)
                || tag.equals("android.widget.GridLayout")
                || tag.endsWith(".GridLayout");
    }

    @Nullable
    private Set<String> getAttributesFromStyle(@NonNull String styleRef) {
        if (mStyleToAttributes == null) {
            return null;
        }

        // Normalize the style reference
        String styleName = normalizeStyleRef(styleRef);

        // Walk up the style hierarchy
        Set<String> result = new HashSet<>();
        Set<String> visited = new HashSet<>();

        String current = styleName;
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            Set<String> attrs = mStyleToAttributes.get(current);
            if (attrs != null) {
                result.addAll(attrs);
            }
            if (mStyleParents != null) {
                current = mStyleParents.get(current);
            } else {
                break;
            }
        }

        return result.isEmpty() ? null : result;
    }

    @NonNull
    private static String normalizeStyleRef(@NonNull String ref) {
        if (ref.startsWith("@style/")) {
            ref = ref.substring("@style/".length());
        } else if (ref.startsWith("@android:style/")) {
            ref = ref.substring("@android:style/".length());
        } else if (ref.startsWith("?attr/")) {
            ref = ref.substring("?attr/".length());
        }
        return ref;
    }
}