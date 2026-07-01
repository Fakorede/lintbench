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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class StateListDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a state qualifier. " +
            "If not, all subsequent items in the list will be ignored since the given item will match all.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        int count = children.getLength();
        for (int i = 0; i < count; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                boolean hasState = false;
                NamedNodeMap attributes = item.getAttributes();
                if (attributes != null) {
                    for (int j = 0, m = attributes.getLength(); j < m; j++) {
                        Attr attr = (Attr) attributes.item(j);
                        String name = attr.getLocalName();
                        if (name == null) {
                            name = attr.getNodeName();
                        }
                        if (name != null && name.startsWith("state_")) {
                            hasState = true;
                            break;
                        }
                    }
                }
                if (!hasState) {
                    boolean hasSubsequentElements = false;
                    for (int k = i + 1; k < count; k++) {
                        if (children.item(k).getNodeType() == Node.ELEMENT_NODE) {
                            hasSubsequentElements = true;
                            break;
                        }
                    }
                    if (hasSubsequentElements) {
                        context.report(ISSUE, context.getLocation(item),
                                "This item matches all states and is not last, so subsequent items will be ignored");
                    }
                }
            }
        }
    }
}