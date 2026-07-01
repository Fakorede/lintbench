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
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in selector",
                    "In a selector, only the last child in the state list should omit a state "
                            + "qualifier. If not, all subsequent items in the list will be "
                            + "ignored since the given item will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        visitElement(context, document.getDocumentElement());
    }

    private static void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("selector".equals(element.getTagName())) {
            checkSelector(context, element);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                visitElement(context, (Element) child);
            }
        }
    }

    private static void checkSelector(@NonNull XmlContext context, @NonNull Element selector) {
        List<Element> items = new ArrayList<>();
        NodeList children = selector.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "item".equals(((Element) child).getTagName())) {
                items.add((Element) child);
            }
        }

        for (int i = 0; i < items.size() - 1; i++) {
            Element item = items.get(i);
            if (!hasStateAttribute(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is missing a state qualifier and will match all states, "
                                + "making subsequent items unreachable");
            }
        }
    }

    private static boolean hasStateAttribute(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}