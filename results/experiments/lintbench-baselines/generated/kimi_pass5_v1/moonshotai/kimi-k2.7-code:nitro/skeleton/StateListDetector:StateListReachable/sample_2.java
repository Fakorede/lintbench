package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class StateListDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a `<selector>`",
                    "In a `<selector>`, only the last item should omit state qualifiers. "
                            + "If an item before the last one has no state qualifiers, it matches all states "
                            + "and the following items in the selector will never be reached.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.COLOR;
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.NodeList selectors = document.getElementsByTagName("selector");
        for (int i = 0; i < selectors.getLength(); i++) {
            org.w3c.dom.Node selectorNode = selectors.item(i);
            if (selectorNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            org.w3c.dom.Element selector = (org.w3c.dom.Element) selectorNode;
            org.w3c.dom.NodeList children = selector.getChildNodes();

            int itemCount = 0;
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node child = children.item(j);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                        && "item".equals(child.getNodeName())) {
                    itemCount++;
                }
            }

            if (itemCount == 0) {
                continue;
            }

            int index = 0;
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node child = children.item(j);
                if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE
                        || !"item".equals(child.getNodeName())) {
                    continue;
                }
                index++;
                if (index == itemCount) {
                    break;
                }
                org.w3c.dom.Element item = (org.w3c.dom.Element) child;
                if (!hasStateAttribute(item)) {
                    context.report(
                            ISSUE,
                            item,
                            context.getLocation(item),
                            "This selector item has no state qualifiers and will match all states, "
                                    + "so the following items will be ignored");
                }
            }
        }
    }

    private boolean hasStateAttribute(org.w3c.dom.Element item) {
        org.w3c.dom.NamedNodeMap attrs = item.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            org.w3c.dom.Node attr = attrs.item(i);
            if (attr.getNodeType() == org.w3c.dom.Node.ATTRIBUTE_NODE) {
                org.w3c.dom.Attr a = (org.w3c.dom.Attr) attr;
                String localName = a.getLocalName();
                if (localName != null
                        && localName.startsWith("state_")
                        && ANDROID_URI.equals(a.getNamespaceURI())) {
                    return true;
                }
            }
        }
        return false;
    }
}