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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

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

        List<Element> children = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) node);
            }
        }

        int catchAllIndex = -1;
        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            if (isCatchAll(child)) {
                catchAllIndex = i;
                break;
            }
        }

        if (catchAllIndex >= 0 && catchAllIndex < children.size() - 1) {
            for (int i = catchAllIndex + 1; i < children.size(); i++) {
                Element unreachable = children.get(i);
                context.report(
                        ISSUE,
                        unreachable,
                        context.getLocation(unreachable),
                        "This item is unreachable because a previous item (item #"
                                + (catchAllIndex + 1)
                                + ") is a more general match than this one");
            }
        }
    }

    private static boolean isCatchAll(@NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null || attributes.getLength() == 0) {
            return true;
        }

        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return false;
            }
            // Also check by full name in case namespace processing isn't active
            String name = attr.getName();
            if (name != null && (name.startsWith("state_") || name.contains(":state_"))) {
                return false;
            }
            // Check namespace URI
            String ns = attr.getNamespaceURI();
            if (ANDROID_NS.equals(ns) && localName != null && localName.startsWith("state_")) {
                return false;
            }
        }

        return true;
    }
}