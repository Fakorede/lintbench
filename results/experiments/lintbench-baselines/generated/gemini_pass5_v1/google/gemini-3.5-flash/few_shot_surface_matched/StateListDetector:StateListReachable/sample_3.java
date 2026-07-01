package com.android.tools.lint.checks;

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
import java.util.ArrayList;
import java.util.List;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a <selector>",
                    "In a selector, only the last child in the state list should omit a state qualifier. "
                            + "If not, all subsequent items in the list will be ignored since the given item "
                            + "will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            StateListDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.COLOR;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"selector".equals(root.getTagName())) {
            return;
        }

        List<Element> items = new ArrayList<>();
        NodeList childNodes = root.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                items.add((Element) child);
            }
        }

        for (int i = 0; i < items.size() - 1; i++) {
            Element item = items.get(i);
            if (!hasStateAttributes(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "An item in a StateList with no state specifiers must be the last item");
            }
        }
    }

    private boolean hasStateAttributes(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                String namespace = attr.getNamespaceURI();
                if ("http://schemas.android.com/apk/res/android".equals(namespace)) {
                    return true;
                }
            }
        }
        return false;
    }
}