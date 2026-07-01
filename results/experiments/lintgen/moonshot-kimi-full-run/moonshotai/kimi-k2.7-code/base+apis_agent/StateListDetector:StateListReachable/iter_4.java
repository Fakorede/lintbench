package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StateListDetector extends Detector implements Detector.XmlScanner {
    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a " +
                    "state qualifier. If not, all subsequent items in the list will be " +
                    "ignored since the given item will match all.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        List<Element> items = getItemElements(element);
        for (int i = 0, n = items.size(); i < n - 1; i++) {
            Element item = items.get(i);
            if (!hasStateAttribute(item)) {
                context.report(ISSUE, item, context.getLocation(item),
                        "This item will match all states, making subsequent items unreachable; " +
                                "only the last item in a selector should omit state qualifiers.");
            }
        }
    }

    private static List<Element> getItemElements(Element selector) {
        List<Element> items = new ArrayList<>();
        NodeList children = selector.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "item".equals(child.getLocalName())) {
                items.add((Element) child);
            }
        }
        return items;
    }

    private static boolean hasStateAttribute(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getLocalName();
            if (name != null && name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}