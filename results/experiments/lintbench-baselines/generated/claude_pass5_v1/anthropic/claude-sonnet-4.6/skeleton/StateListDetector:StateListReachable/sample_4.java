package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a `<selector>`",
                    "In a selector, only the last child in the state list should omit a "
                            + "state qualifier. If not, all subsequent items in the list will be ignored "
                            + "since the given item will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        visitElement(context, root);
    }

    private void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("selector".equals(tagName)) {
            checkSelector(context, element);
        }

        // Recurse into children
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                visitElement(context, (Element) child);
            }
        }
    }

    private void checkSelector(@NonNull XmlContext context, @NonNull Element selector) {
        NodeList children = selector.getChildNodes();
        int childCount = children.getLength();

        Element defaultItem = null;

        for (int i = 0; i < childCount; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) node;

            if (defaultItem != null) {
                // We already found a default (catch-all) item, and there are more items after it.
                // Report the current item as unreachable.
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item #"
                                + getItemIndex(selector, defaultItem)
                                + ") is a default which matches everything");
            } else if (!hasStateAttribute(item)) {
                // This item has no state qualifier — it's a catch-all/default item.
                defaultItem = item;
            }
        }
    }

    /**
     * Returns true if the given {@code <item>} element has at least one {@code android:state_*}
     * attribute.
     */
    private static boolean hasStateAttribute(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attr = attributes.item(i);
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
            // Also handle the case where the attribute name is prefixed (e.g. "android:state_*")
            String name = attr.getNodeName();
            if (name != null) {
                int colon = name.indexOf(':');
                String local = colon >= 0 ? name.substring(colon + 1) : name;
                if (local.startsWith("state_")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the 1-based index of {@code target} among element children of {@code parent}.
     */
    private static int getItemIndex(@NonNull Element parent, @NonNull Element target) {
        NodeList children = parent.getChildNodes();
        int index = 0;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                index++;
                if (node == target) {
                    return index;
                }
            }
        }
        return -1;
    }
}