package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StateListDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a state " +
            "qualifier. If not, all subsequent items in the list will be ignored since " +
            "the given item will match all.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    StateListDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String TAG_SELECTOR = "selector";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SELECTOR);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        List<Element> items = new ArrayList<>();

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                items.add((Element) child);
            }
        }

        for (int i = 0; i < items.size() - 1; i++) {
            Element item = items.get(i);
            if (!hasStateAttribute(item)) {
                // This item is a catch-all but is not the last item
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is a catch-all and will make all subsequent items unreachable"
                );
                // Report subsequent items as unreachable
                for (int j = i + 1; j < items.size(); j++) {
                    Element unreachable = items.get(j);
                    context.report(
                            ISSUE,
                            unreachable,
                            context.getLocation(unreachable),
                            "This item is unreachable because a previous item is a catch-all " +
                            "(has no state qualifier)"
                    );
                }
                break;
            }
        }
    }

    private static boolean hasStateAttribute(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
            String name = attr.getName();
            if (name != null && (name.startsWith("state_") || name.contains(":state_"))) {
                return true;
            }
        }
        return false;
    }
}