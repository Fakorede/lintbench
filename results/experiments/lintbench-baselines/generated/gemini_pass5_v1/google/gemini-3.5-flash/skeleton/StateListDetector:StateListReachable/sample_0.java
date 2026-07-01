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
                    "In a selector, only the last child in the state list should omit a state qualifier. " +
                    "If not, all subsequent items in the list will be ignored since the given item will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.COLOR 
                || folderType == ResourceFolderType.DRAWABLE 
                || folderType == ResourceFolderType.ANIMATOR 
                || folderType == ResourceFolderType.XML;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("selector")) {
            return;
        }

        NodeList children = root.getChildNodes();
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.getTagName().equals("item")) {
                    items.add(child);
                }
            }
        }

        for (int i = 0; i < items.size() - 1; i++) {
            Element item = items.get(i);
            if (!hasStateQualifier(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item matches all states, so any subsequent items will be ignored");
            }
        }
    }

    private boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                String localName = attr.getLocalName();
                if (localName == null) {
                    localName = attr.getNodeName();
                    int colon = localName.indexOf(':');
                    if (colon != -1) {
                        localName = localName.substring(colon + 1);
                    }
                }
                if (localName.startsWith("state_")) {
                    return true;
                }
            }
        }
        return false;
    }
}