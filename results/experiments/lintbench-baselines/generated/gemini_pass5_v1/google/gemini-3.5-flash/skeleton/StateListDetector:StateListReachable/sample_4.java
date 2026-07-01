package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a `<selector>`",
                    "In a selector, only the last child in the state list should omit a "
                            + "state qualifier. If not, all subsequent items in the list will be ignored "
                            + "since the given item will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"selector".equals(root.getTagName())) {
            return;
        }

        NodeList children = root.getChildNodes();
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                items.add((Element) child);
            }
        }

        int numItems = items.size();
        for (int i = 0; i < numItems - 1; i++) {
            Element item = items.get(i);
            if (!hasStateAttributes(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item will match all states, so any subsequent items will be ignored");
                break;
            }
        }
    }

    private boolean hasStateAttributes(@NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getLocalName();
            if (name == null) {
                name = attribute.getNodeName();
                int index = name.indexOf(':');
                if (index != -1) {
                    name = name.substring(index + 1);
                }
            }
            if (name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}