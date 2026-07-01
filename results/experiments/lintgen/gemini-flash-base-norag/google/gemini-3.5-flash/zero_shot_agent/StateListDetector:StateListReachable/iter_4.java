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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a " +
            "state qualifier. If not, all subsequent items in the list will be ignored " +
            "since the given item will match all.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    StateListDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        int numChildren = children.getLength();
        int itemIndex = 0;
        for (int i = 0; i < numChildren; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String tagName = item.getTagName();
                if (tagName.equals("item")) {
                    boolean hasState = false;
                    NamedNodeMap attributes = item.getAttributes();
                    for (int j = 0; j < attributes.getLength(); j++) {
                        Node attribute = attributes.item(j);
                        String name = attribute.getLocalName();
                        if (name == null) {
                            name = attribute.getNodeName();
                            int colon = name.indexOf(':');
                            if (colon != -1) {
                                name = name.substring(colon + 1);
                            }
                        }
                        if (name.startsWith("state_")) {
                            hasState = true;
                            break;
                        }
                    }

                    if (!hasState) {
                        for (int j = i + 1; j < numChildren; j++) {
                            Node subsequent = children.item(j);
                            if (subsequent.getNodeType() == Node.ELEMENT_NODE) {
                                Element subsequentElement = (Element) subsequent;
                                if (subsequentElement.getTagName().equals("item")) {
                                    context.report(
                                            ISSUE,
                                            subsequentElement,
                                            context.getLocation(subsequentElement),
                                            String.format(
                                                    "This item is unreachable because a previous item (%1$d) has no restrictions (it is the default state)",
                                                    itemIndex + 1
                                            )
                                    );
                                }
                            }
                        }
                        break;
                    }
                    itemIndex++;
                }
            }
        }
    }
}