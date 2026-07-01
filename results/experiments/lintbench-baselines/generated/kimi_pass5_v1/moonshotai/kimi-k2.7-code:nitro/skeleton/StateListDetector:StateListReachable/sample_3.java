package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a `<selector>`",
                    "In a `<selector>`, only the last item should omit a state qualifier. "
                            + "If an earlier item has no state qualifier, it will match all "
                            + "states and all subsequent items will be ignored.",
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
        visitNode(context, document.getDocumentElement());
    }

    private static void visitNode(@NonNull XmlContext context, @NonNull Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element element = (Element) node;
        if ("selector".equals(element.getNodeName())) {
            checkSelector(context, element);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            visitNode(context, children.item(i));
        }
    }

    private static void checkSelector(@NonNull XmlContext context, @NonNull Element selector) {
        List<Element> items = new ArrayList<>();
        NodeList children = selector.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                items.add((Element) child);
            }
        }

        if (items.isEmpty()) {
            return;
        }

        int lastIndex = items.size() - 1;
        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            if (i != lastIndex && isDefaultItem(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This default item is not the last state in the selector; "
                                + "later states will be unreachable");
            }
        }
    }

    private static boolean isDefaultItem(@NonNull Element item) {
        NodeList attributes = item.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attribute = attributes.item(i);
            String localName = attribute.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return false;
            }
        }
        return true;
    }
}