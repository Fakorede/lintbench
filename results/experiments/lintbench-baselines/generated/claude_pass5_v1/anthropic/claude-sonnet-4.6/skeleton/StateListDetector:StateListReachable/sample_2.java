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
        boolean foundUnqualifiedItem = false;
        Element unqualifiedElement = null;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) child;

            if (!"item".equals(item.getTagName())) {
                continue;
            }

            if (foundUnqualifiedItem) {
                // This item comes after an unqualified item — it is unreachable
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item #"
                                + getItemIndex(root, unqualifiedElement)
                                + ") is not constrained by any state "
                                + "qualifier and will always be selected");
            } else if (!hasStateQualifier(item)) {
                foundUnqualifiedItem = true;
                unqualifiedElement = item;
            }
        }
    }

    /**
     * Returns true if the given {@code <item>} element has at least one android:state_* attribute.
     */
    private static boolean hasStateQualifier(@NonNull Element item) {
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
            // Also handle the case where the attribute name includes the namespace prefix
            String nodeName = attr.getNodeName();
            if (nodeName != null) {
                int colon = nodeName.indexOf(':');
                String name = colon >= 0 ? nodeName.substring(colon + 1) : nodeName;
                if (name.startsWith("state_")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the 1-based index of the given item element among {@code <item>} children of root.
     */
    private static int getItemIndex(@NonNull Element root, @NonNull Element target) {
        NodeList children = root.getChildNodes();
        int index = 0;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!"item".equals(element.getTagName())) {
                continue;
            }
            index++;
            if (element == target) {
                return index;
            }
        }
        return index;
    }
}