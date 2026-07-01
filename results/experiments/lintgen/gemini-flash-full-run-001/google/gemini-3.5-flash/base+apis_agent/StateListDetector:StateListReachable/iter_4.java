package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a "
                    + "state qualifier. If not, all subsequent items in the list will be ignored "
                    + "since the given item will match all.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        int childCount = children.getLength();
        Element firstWithout = null;
        for (int i = 0; i < childCount; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String tagName = item.getLocalName();
                if (tagName == null) {
                    tagName = item.getTagName();
                }
                if ("item".equals(tagName)) {
                    if (firstWithout == null) {
                        if (!hasStateQualifier(item)) {
                            firstWithout = item;
                        }
                    } else {
                        context.report(
                                ISSUE,
                                item,
                                context.getLocation(item),
                                "This item is unreachable because a previous item has no state qualifiers and will match first"
                        );
                    }
                }
            }
        }
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
            }
            if (name != null && (name.startsWith("state_") || name.contains(":state_"))) {
                return true;
            }
        }
        return false;
    }
}