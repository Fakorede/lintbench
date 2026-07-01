package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class StateListDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a <selector>",
                    "In a selector, only the last child in the state list should omit a "
                            + "state qualifier. If not, all subsequent items in the list will "
                            + "be ignored since the given item will match all.",
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
        boolean foundCatchAll = false;
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            org.w3c.dom.Element item = (org.w3c.dom.Element) child;
            if (!"item".equals(item.getTagName())) {
                continue;
            }

            if (foundCatchAll) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item has no state qualifiers and will match all states");
            } else if (!hasStateQualifier(item)) {
                foundCatchAll = true;
            }
        }
    }

    private boolean hasStateQualifier(org.w3c.dom.Element element) {
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            org.w3c.dom.Node attr = attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
                int colon = name.indexOf(':');
                if (colon != -1) {
                    name = name.substring(colon + 1);
                }
            }
            if (name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}