package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a <selector>",
                    "In a selector, only the last child in the state list should omit a "
                            + "state qualifier. If not, all subsequent items in the list will be "
                            + "ignored since the given item will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"selector".equals(root.getTagName())) {
            return;
        }

        NodeList children = root.getChildNodes();
        int lastItemIndex = -1;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                lastItemIndex = i;
            }
        }

        if (lastItemIndex == -1) {
            return;
        }

        for (int i = 0; i < lastItemIndex; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                Element item = (Element) child;
                if (!hasStateQualifier(item)) {
                    context.report(ISSUE, item, context.getLocation(item),
                            "This item is missing state qualifiers and will match all states, "
                                    + "making subsequent items unreachable");
                }
            }
        }
    }

    private static boolean hasStateQualifier(Element item) {
        if (!item.hasAttributes()) {
            return false;
        }
        for (int i = 0, n = item.getAttributes().getLength(); i < n; i++) {
            Node attr = item.getAttributes().item(i);
            String ns = attr.getNamespaceURI();
            String name = attr.getLocalName();
            if ("http://schemas.android.com/apk/res/android".equals(ns)
                    && name != null && name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}