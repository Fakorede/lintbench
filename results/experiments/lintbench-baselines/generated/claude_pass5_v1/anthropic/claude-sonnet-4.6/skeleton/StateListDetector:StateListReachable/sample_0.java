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
        if (!"selector".equals(root.getTagName())) {
            return;
        }

        NodeList children = root.getChildNodes();
        boolean foundUnconditional = false;
        Element unconditionalElement = null;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) child;
            if (!"item".equals(item.getTagName())) {
                continue;
            }

            if (foundUnconditional) {
                // This item comes after an unconditional item — it is unreachable
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item #"
                                + getItemIndex(root, unconditionalElement)
                                + ") is not constrained by any state and therefore matches "
                                + "anything (and is always chosen over this item)");
            } else if (!hasStateAttribute(item)) {
                foundUnconditional = true;
                unconditionalElement = item;
            }
        }
    }

    /**
     * Returns true if the given {@code <item>} element has at least one {@code android:state_*}
     * attribute.
     */
    private static boolean hasStateAttribute(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attr = attributes.item(i);
            if (attr.getLocalName() != null && attr.getLocalName().startsWith("state_")) {
                return true;
            }
            // Also handle the case where the prefix is included in the name
            String name = attr.getNodeName();
            if (name.contains(":state_")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the 1-based index of the given {@code <item>} element among its {@code <item>}
     * siblings inside the selector.
     */
    private static int getItemIndex(@NonNull Element selector, @NonNull Element item) {
        NodeList children = selector.getChildNodes();
        int index = 0;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!"item".equals(((Element) child).getTagName())) {
                continue;
            }
            index++;
            if (child == item) {
                return index;
            }
        }
        return index;
    }
}