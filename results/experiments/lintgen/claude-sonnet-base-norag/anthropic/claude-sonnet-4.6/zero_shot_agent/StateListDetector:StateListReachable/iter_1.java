package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class StateListDetector extends ResourceXmlDetector {

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
                    Scope.RESOURCE_FILE_SCOPE));

    public StateListDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList childNodes = element.getChildNodes();
        int childCount = childNodes.getLength();

        Element catchAllElement = null;

        for (int i = 0; i < childCount; i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element child = (Element) node;

            if (catchAllElement != null) {
                context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "This item is unreachable because a previous item (item #"
                                + getChildIndex(catchAllElement)
                                + ") is a more general match than this one");
            } else if (isCatchAll(child)) {
                catchAllElement = child;
            }
        }
    }

    private static boolean isCatchAll(@NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return true;
        }

        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            // Check local name first (namespace-aware)
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return false;
            }
            // Also check full attribute name (e.g., "android:state_pressed")
            String fullName = attr.getName();
            if (fullName != null && (fullName.startsWith("state_") || fullName.contains(":state_"))) {
                return false;
            }
        }

        return true;
    }

    private static int getChildIndex(@NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent == null) {
            return 1;
        }

        NodeList siblings = parent.getChildNodes();
        int index = 0;
        for (int i = 0, n = siblings.getLength(); i < n; i++) {
            Node sibling = siblings.item(i);
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                index++;
                if (sibling == element) {
                    return index;
                }
            }
        }

        return index;
    }
}