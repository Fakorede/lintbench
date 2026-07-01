package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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

import java.util.Arrays;
import java.util.Collection;

public class StateListDetector extends ResourceXmlDetector implements Detector.XmlScanner {

    private static final String TAG_SELECTOR = "selector";
    private static final String TAG_ITEM = "item";

    private static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable selector state",
            "In a `<selector>`, only the last child in the state list should omit a state qualifier. "
                    + "If not, all subsequent items in the list will be ignored since the given "
                    + "item will match all.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SELECTOR);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_SELECTOR.equals(element.getTagName())) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) child;
            if (!TAG_ITEM.equals(item.getTagName())) {
                continue;
            }

            if (!hasStateQualifier(item) && !isLastItem(item)) {
                context.report(ISSUE, item, context.getNameLocation(item),
                        "This item matches all states, making later items unreachable; "
                                + "only the last item should omit a state qualifier");
            }
        }
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attrs = item.getAttributes();
        for (int i = 0, n = attrs.getLength(); i < n; i++) {
            Attr attr = (Attr) attrs.item(i);
            String name = attr.getName();
            if (name.startsWith("android:state_")) {
                return true;
            }
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLastItem(Element item) {
        Node sibling = item.getNextSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ITEM.equals(((Element) sibling).getTagName())) {
                return false;
            }
            sibling = sibling.getNextSibling();
        }
        return true;
    }
}