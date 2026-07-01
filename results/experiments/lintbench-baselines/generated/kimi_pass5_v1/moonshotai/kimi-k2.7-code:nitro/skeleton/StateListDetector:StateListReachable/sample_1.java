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
                    "In a `<selector>`, only the last child in the state list should omit a state qualifier. If a non-last item has no state qualifiers, all subsequent items will be ignored because this item matches all states.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"selector".equals(root.getTagName())) {
            return;
        }

        NodeList children = root.getChildNodes();

        int lastItemIndex = -1;
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (isItemElement(child)) {
                lastItemIndex = i;
                break;
            }
        }

        if (lastItemIndex <= 0) {
            return;
        }

        for (int i = 0; i < lastItemIndex; i++) {
            Node child = children.item(i);
            if (!isItemElement(child)) {
                continue;
            }

            Element item = (Element) child;
            if (!hasStateQualifier(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item matches every state and makes all subsequent items unreachable; only the last item should omit state qualifiers");
            }
        }
    }

    private static boolean isItemElement(@NonNull Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }
        String name = node.getLocalName();
        if (name == null) {
            name = node.getNodeName();
        }
        return "item".equals(name);
    }

    private static boolean hasStateQualifier(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
                int colon = name.indexOf(':');
                if (colon != -1) {
                    name = name.substring(colon + 1);
                }
            }
            if (name != null && name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}