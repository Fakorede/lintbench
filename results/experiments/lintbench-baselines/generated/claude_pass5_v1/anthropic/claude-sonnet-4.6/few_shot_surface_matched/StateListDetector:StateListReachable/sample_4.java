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
                    new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

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
        boolean foundCatchAll = false;
        Element catchAllElement = null;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) child;

            if (foundCatchAll) {
                // This item comes after a catch-all item — it is unreachable
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item "
                                + getDescription(catchAllElement)
                                + ") is a catch-all");
            } else if (isCatchAll(item)) {
                foundCatchAll = true;
                catchAllElement = item;
            }
        }
    }

    /**
     * Returns true if the given {@code <item>} element has no {@code android:state_*} attributes,
     * making it a catch-all that matches any state.
     */
    private static boolean isCatchAll(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            String name = attributes.item(i).getLocalName();
            if (name != null && name.startsWith("state_")) {
                return false;
            }
            // Also handle non-namespaced attributes
            String nodeName = attributes.item(i).getNodeName();
            if (nodeName != null) {
                // Strip namespace prefix if present (e.g. "android:state_pressed")
                int colon = nodeName.indexOf(':');
                String localPart = colon >= 0 ? nodeName.substring(colon + 1) : nodeName;
                if (localPart.startsWith("state_")) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String getDescription(@NonNull Element element) {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(element.getTagName());
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attr = attributes.item(i);
            sb.append(' ').append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append('"');
        }
        sb.append('>');
        return sb.toString();
    }
}