package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.SdkConstants.VIEW_INCLUDE;

public class RequiredAttributeDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime.\n\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special " +
            "case, does not require you to specify a size.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Map from style name to set of attribute names defined in that style */
    private Map<String, Set<String>> mStyleToAttributes;

    /** Map from style name to parent style name */
    private Map<String, String> mStyleParents;

    /** Pending elements that reference styles not yet fully processed */
    private List<PendingElement> mPendingElements;

    /** Whether we're currently processing a values file (styles) */
    private boolean mInValuesFile;

    private static class PendingElement {
        final XmlContext context;
        final Element element;
        final String styleName;
        final boolean missingWidth;
        final boolean missingHeight;

        PendingElement(XmlContext context, Element element, String styleName,
                boolean missingWidth, boolean missingHeight) {
            this.context = context;
            this.element = element;
            this.styleName = styleName;
            this.missingWidth = missingWidth;
            this.missingHeight = missingHeight;
        }
    }

    public RequiredAttributeDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_STYLE);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.LAYOUT) {
            mInValuesFile = false;
            checkLayoutDocument(context, document);
        } else if (folderType == ResourceFolderType.VALUES) {
            mInValuesFile = true;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // This handles TAG_STYLE elements in values files
        if (!mInValuesFile) {
            return;
        }
        String tagName = element.getTagName();
        if (!TAG_STYLE.equals(tagName)) {
            return;
        }

        String styleName = element.getAttribute("name");
        if (styleName == null || styleName.isEmpty()) {
            return;
        }

        // Normalize style name (replace dots with underscores for lookup)
        styleName = normalizeStyleName(styleName);

        String parent = element.getAttribute("parent");
        if (parent != null && !parent.isEmpty()) {
            parent = normalizeStyleRef(parent);
            if (parent != null) {
                if (mStyleParents == null) {
                    mStyleParents = new HashMap<>();
                }
                mStyleParents.put(styleName, parent);
            }
        } else {
            // Check for implicit parent via dot notation in original name
            String originalName = element.getAttribute("name");
            int lastDot = originalName.lastIndexOf('.');
            if (lastDot > 0) {
                String implicitParent = normalizeStyleName(originalName.substring(0, lastDot));
                if (mStyleParents == null) {
                    mStyleParents = new HashMap<>();
                }
                mStyleParents.put(styleName, implicitParent);
            }
        }

        // Collect attributes defined in this style
        Set<String> attrs = new HashSet<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String itemName = item.getAttribute("name");
                if (itemName != null) {
                    // Strip android: prefix for comparison
                    if (itemName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                        itemName = itemName.substring(ANDROID_NS_NAME_PREFIX.length());
                    }
                    attrs.add(itemName);
                }
            }
        }

        if (mStyleToAttributes == null) {
            mStyleToAttributes = new HashMap<>();
        }
        mStyleToAttributes.put(styleName, attrs);
    }

    private void checkLayoutDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        // Skip merge and include tags at root - they don't need layout params
        // Actually, include does need layout params if it's not the root
        // merge never needs layout params
        if (VIEW_MERGE.equals(tag)) {
            // Check children
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    checkElement(context, (Element) child);
                }
            }
            return;
        }

        // Check if this element needs layout_width and layout_height
        // Root elements don't need them (they are the root of the layout)
        Node parent = element.getParentNode();
        boolean isRoot = (parent == null || parent.getNodeType() == Node.DOCUMENT_NODE);

        if (!isRoot) {
            // GridLayout children don't need layout_width/height
            boolean parentIsGridLayout = false;
            if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                String parentTag = ((Element) parent).getTagName();
                parentIsGridLayout = GRID_LAYOUT.equals(parentTag)
                        || parentTag.endsWith(".GridLayout");
            }

            if (!parentIsGridLayout && !GRID_LAYOUT.equals(tag) && !tag.endsWith(".GridLayout")) {
                boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

                if (!hasWidth || !hasHeight) {
                    // Check style attribute
                    String styleRef = element.getAttribute(ATTR_STYLE);
                    if (styleRef != null && !styleRef.isEmpty()) {
                        String styleName = normalizeStyleRef(styleRef);
                        if (styleName != null) {
                            if (mStyleToAttributes != null) {
                                boolean styleHasWidth = hasWidth || styleDefinesAttribute(styleName, ATTR_LAYOUT_WIDTH);
                                boolean styleHasHeight = hasHeight || styleDefinesAttribute(styleName, ATTR_LAYOUT_HEIGHT);
                                if (!styleHasWidth || !styleHasHeight) {
                                    // Defer - style might be defined in another file
                                    addPending(context, element, styleName, !hasWidth, !hasHeight);
                                }
                            } else {
                                // No styles processed yet - defer
                                addPending(context, element, styleName, !hasWidth, !hasHeight);
                            }
                        } else {
                            // Can't resolve style name
                            reportMissing(context, element, !hasWidth, !hasHeight);
                        }
                    } else {
                        reportMissing(context, element, !hasWidth, !hasHeight);
                    }
                }
            }
        }

        // Check children
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    private void addPending(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String styleName, boolean missingWidth, boolean missingHeight) {
        if (mPendingElements == null) {
            mPendingElements = new ArrayList<>();
        }
        mPendingElements.add(new PendingElement(context, element, styleName,
                missingWidth, missingHeight));
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mPendingElements != null) {
            for (PendingElement pending : mPendingElements) {
                boolean missingWidth = pending.missingWidth;
                boolean missingHeight = pending.missingHeight;

                if (mStyleToAttributes != null) {
                    if (missingWidth) {
                        missingWidth = !styleDefinesAttribute(pending.styleName, ATTR_LAYOUT_WIDTH);
                    }
                    if (missingHeight) {
                        missingHeight = !styleDefinesAttribute(pending.styleName, ATTR_LAYOUT_HEIGHT);
                    }
                }

                if (missingWidth || missingHeight) {
                    reportMissing(pending.context, pending.element, missingWidth, missingHeight);
                }
            }
            mPendingElements = null;
        }
    }

    private boolean styleDefinesAttribute(@NonNull String styleName, @NonNull String attribute) {
        if (mStyleToAttributes == null) {
            return false;
        }

        // Prevent infinite loops
        Set<String> visited = new HashSet<>();
        return styleDefinesAttributeRecursive(styleName, attribute, visited);
    }

    private boolean styleDefinesAttributeRecursive(@NonNull String styleName,
            @NonNull String attribute, @NonNull Set<String> visited) {
        if (visited.contains(styleName)) {
            return false;
        }
        visited.add(styleName);

        if (mStyleToAttributes != null) {
            Set<String> attrs = mStyleToAttributes.get(styleName);
            if (attrs != null && attrs.contains(attribute)) {
                return true;
            }
        }

        // Check parent style
        if (mStyleParents != null) {
            String parent = mStyleParents.get(styleName);
            if (parent != null) {
                return styleDefinesAttributeRecursive(parent, attribute, visited);
            }
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

    @Nullable
    private static String normalizeStyleRef(@NonNull String styleRef) {
        // Style references can be:
        // @style/StyleName
        // @android:style/StyleName
        // StyleName (direct)
        if (styleRef.startsWith("@")) {
            int slashIndex = styleRef.indexOf('/');
            if (slashIndex != -1) {
                return normalizeStyleName(styleRef.substring(slashIndex + 1));
            }
            return null;
        }
        return normalizeStyleName(styleRef);
    }

    @NonNull
    private static String normalizeStyleName(@NonNull String name) {
        return name.replace('.', '_');
    }
}