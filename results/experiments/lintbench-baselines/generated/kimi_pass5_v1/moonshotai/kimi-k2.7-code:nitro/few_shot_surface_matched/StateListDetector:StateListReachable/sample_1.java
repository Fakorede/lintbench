package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class StateListDetector extends ResourceXmlDetector implements com.android.tools.lint.detector.api.XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in state list",
                    "In a selector, only the last child in the state list should omit a state qualifier. "
                            + "If an earlier item omits a state qualifier, it will match all states and "
                            + "subsequent items in the list will be ignored.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !"selector".equals(root.getTagName())) {
            return;
        }

        org.w3c.dom.NodeList children = root.getChildNodes();
        int childCount = children.getLength();

        int itemCount = 0;
        for (int i = 0; i < childCount; i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                if ("item".equals(element.getTagName())) {
                    itemCount++;
                }
            }
        }

        if (itemCount == 0) {
            return;
        }

        int index = 0;
        for (int i = 0; i < childCount; i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }

            org.w3c.dom.Element element = (org.w3c.dom.Element) node;
            if (!"item".equals(element.getTagName())) {
                continue;
            }

            if (index < itemCount - 1 && !hasStateQualifier(element)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "This item does not specify a state qualifier, so it will match all "
                                + "states and hide later items. Only the last item in a selector "
                                + "may omit state qualifiers.");
            }

            index++;
        }
    }

    private static boolean hasStateQualifier(org.w3c.dom.Element element) {
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        int length = attributes.getLength();
        for (int i = 0; i < length; i++) {
            org.w3c.dom.Node attribute = attributes.item(i);
            String localName = attribute.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}