package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a `<selector>`",
                    "In a selector, only the last child in the state list should omit a "
                            + "state qualifier. If not, all subsequent items in the list will be "
                            + "ignored since the given item will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.COLOR;
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !"selector".equals(root.getTagName())) {
            return;
        }

        org.w3c.dom.NodeList children = root.getChildNodes();
        int childCount = children.getLength();
        java.util.List<org.w3c.dom.Element> items = new java.util.ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                items.add((org.w3c.dom.Element) child);
            }
        }

        int numItems = items.size();
        for (int i = 0; i < numItems - 1; i++) {
            org.w3c.dom.Element item = items.get(i);
            if (!hasStateQualifier(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item has no state, so it will match first and match all. "
                                + "Subsequent items will not be reached.");
            }
        }
    }

    private boolean hasStateQualifier(org.w3c.dom.Element item) {
        org.w3c.dom.NamedNodeMap attributes = item.getAttributes();
        int length = attributes.getLength();
        for (int i = 0; i < length; i++) {
            org.w3c.dom.Node attrNode = attributes.item(i);
            if (attrNode instanceof org.w3c.dom.Attr) {
                org.w3c.dom.Attr attr = (org.w3c.dom.Attr) attrNode;
                String name = attr.getLocalName();
                if (name != null && name.startsWith("state_")) {
                    String namespace = attr.getNamespaceURI();
                    if ("http://schemas.android.com/apk/res/android".equals(namespace)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}