package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
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
                    "A `<selector>` item with no state qualifiers matches all states, so it "
                            + "should be the last item in the list. If it appears earlier, all "
                            + "subsequent items will be unreachable.",
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
        if (root == null || !"selector".equals(root.getTagName())) {
            return;
        }

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !"item".equals(child.getNodeName())) {
                continue;
            }
            Element item = (Element) child;
            if (isDefaultItem(item) && i != getLastItemIndex(children)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This default `<item>` is not the last item in the `<selector>`; "
                                + "subsequent items will never be reached.");
            }
        }
    }

    private static int getLastItemIndex(@NonNull NodeList children) {
        int last = -1;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "item".equals(child.getNodeName())) {
                last = i;
            }
        }
        return last;
    }

    private static boolean isDefaultItem(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attrNode = attributes.item(i);
            if (attrNode.getNodeType() != Node.ATTRIBUTE_NODE) {
                continue;
            }
            Attr attr = (Attr) attrNode;
            String localName = attr.getLocalName();
            if (localName == null) {
                String name = attr.getName();
                int colon = name.indexOf(':');
                localName = colon != -1 ? name.substring(colon + 1) : name;
            }
            if (localName != null && localName.startsWith("state_")) {
                return false;
            }
        }
        return true;
    }
}