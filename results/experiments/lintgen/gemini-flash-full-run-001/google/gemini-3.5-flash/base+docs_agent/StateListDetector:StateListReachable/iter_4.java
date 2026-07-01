package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
        return folderType == ResourceFolderType.DRAWABLE 
                || folderType == ResourceFolderType.COLOR
                || folderType == ResourceFolderType.ANIMATOR;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        int childCount = children.getLength();
        if (childCount > 0) {
            Element firstDefault = null;
            for (int i = 0; i < childCount; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element item = (Element) child;
                    if (item.getTagName().equals("item")) {
                        if (firstDefault != null) {
                            int line = context.getLineNumber(firstDefault);
                            String message = String.format(
                                    "This item is unreachable because a previous item (line %1$d) has no state qualifiers and will match all states",
                                    line
                            );
                            context.report(
                                    ISSUE,
                                    item,
                                    context.getLocation(item),
                                    message
                            );
                        } else if (!hasStateQualifier(item)) {
                            firstDefault = item;
                        }
                    }
                }
            }
        }
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getLocalName();
            if (name == null) {
                name = attribute.getNodeName();
                int index = name.indexOf(':');
                if (index != -1) {
                    name = name.substring(index + 1);
                }
            }
            if (name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}