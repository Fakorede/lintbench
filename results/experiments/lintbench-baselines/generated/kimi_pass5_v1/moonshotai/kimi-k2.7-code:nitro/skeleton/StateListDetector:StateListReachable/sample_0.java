package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
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
                    "In a `<selector>`, only the last `<item>` should omit state qualifiers. "
                            + "If a default item (one without any `state_*` attributes) appears "
                            + "earlier, it matches every state and causes subsequent items to be "
                            + "ignored.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

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
        int childCount = children.getLength();

        int lastItemIndex = -1;
        for (int i = 0; i < childCount; i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && "item".equals(((Element) node).getTagName())) {
                lastItemIndex = i;
            }
        }

        if (lastItemIndex == -1) {
            return;
        }

        for (int i = 0; i < childCount; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) node;
            if (!"item".equals(item.getTagName())) {
                continue;
            }

            if (i == lastItemIndex) {
                continue;
            }

            if (!hasStateAttribute(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This default item matches all states and makes subsequent items unreachable; "
                                + "only the last item in a selector should omit state qualifiers");
            }
        }
    }

    private static boolean hasStateAttribute(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return false;
        }

        int length = attributes.getLength();
        for (int i = 0; i < length; i++) {
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
}