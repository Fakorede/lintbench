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
                    "Unreachable state in a <selector>",
                    "In a selector, only the last child in the state list should omit a state "
                            + "qualifier. If not, all subsequent items in the list will be ignored "
                            + "since the given item will match all.",
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
        java.util.List<org.w3c.dom.Element> items = new java.util.ArrayList<org.w3c.dom.Element>();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element element = (org.w3c.dom.Element) child;
                if ("item".equals(element.getTagName())) {
                    items.add(element);
                }
            }
        }

        boolean foundDefault = false;
        for (org.w3c.dom.Element item : items) {
            if (foundDefault) {
                context.report(ISSUE, item, context.getLocation(item),
                        "This item is unreachable because a previous item matches all states");
            } else if (!hasStateAttributes(item)) {
                foundDefault = true;
            }
        }
    }

    private boolean hasStateAttributes(org.w3c.dom.Element item) {
        org.w3c.dom.NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            org.w3c.dom.Node attr = attributes.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getNodeName();
                int colon = localName.indexOf(':');
                if (colon != -1) {
                    localName = localName.substring(colon + 1);
                }
            }
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}