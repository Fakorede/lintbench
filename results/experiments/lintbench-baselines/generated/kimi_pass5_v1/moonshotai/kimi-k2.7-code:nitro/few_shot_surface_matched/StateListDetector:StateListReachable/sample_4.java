package com.android.tools.lint.checks;

import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_SELECTOR;

import com.android.annotations.NonNull;
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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class StateListDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in state list",
                    "In a selector, only the last child in the state list should omit a state "
                            + "qualifier. If not, all subsequent items in the list will be "
                            + "ignored since the given item will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        return root != null && TAG_SELECTOR.equals(root.getTagName());
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        int itemCount = 0;
        Node node = root.getFirstChild();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(node.getNodeName())) {
                itemCount++;
            }
            node = node.getNextSibling();
        }

        int itemIndex = 0;
        node = root.getFirstChild();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(node.getNodeName())) {
                Element item = (Element) node;
                if (itemIndex < itemCount - 1 && !hasStateQualifier(item)) {
                    context.report(
                            ISSUE,
                            item,
                            context.getLocation(item),
                            "This item is not the last item in the selector and has no state "
                                    + "qualifiers; it will match all states and subsequent items "
                                    + "will be ignored");
                }
                itemIndex++;
            }
            node = node.getNextSibling();
        }
    }

    private static boolean hasStateQualifier(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        int length = attributes.getLength();
        for (int i = 0; i < length; i++) {
            Node attr = attributes.item(i);
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}