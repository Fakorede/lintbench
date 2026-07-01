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
                    "In a selector, only the last child in the state list should omit a state "
                            + "qualifier. If not, all subsequent items in the list will be ignored "
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

        checkSelector(context, root);
    }

    private void checkSelector(@NonNull XmlContext context, @NonNull Element selector) {
        NodeList children = selector.getChildNodes();
        int childCount = children.getLength();

        boolean foundUnconditional = false;
        Element unconditionalElement = null;

        for (int i = 0; i < childCount; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) node;

            if (foundUnconditional) {
                // This item comes after an unconditional item — it's unreachable
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item #"
                                + getItemIndex(unconditionalElement, selector)
                                + ") matches all states and is not the last item");
            }

            if (!hasStateAttribute(item)) {
                foundUnconditional = true;
                unconditionalElement = item;
            }
        }
    }

    /**
     * Returns true if the given element has at least one android:state_* attribute.
     */
    private static boolean hasStateAttribute(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
            }
            if (name != null && name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the 1-based index of the given item element among its element siblings
     * within the parent selector.
     */
    private static int getItemIndex(@NonNull Element item, @NonNull Element selector) {
        NodeList children = selector.getChildNodes();
        int index = 1;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (node == item) {
                return index;
            }
            index++;
        }
        return -1;
    }
}