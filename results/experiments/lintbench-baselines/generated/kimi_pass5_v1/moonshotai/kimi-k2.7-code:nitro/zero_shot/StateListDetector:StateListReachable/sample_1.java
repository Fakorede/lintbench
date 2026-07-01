package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class StateListDetector extends ResourceXmlDetector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a selector",
            "In a selector, only the last child in the state list should omit a state qualifier. "
                    + "If not, all subsequent items in the list will be ignored since the given "
                    + "item will match all.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String SELECTOR = "selector";
    private static final String ITEM = "item";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String STATE_PREFIX = "state_";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ITEM);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (!(parent instanceof Element) || !SELECTOR.equals(((Element) parent).getLocalName())) {
            return;
        }

        if (hasStateQualifier(element) || isLastElementChild(element)) {
            return;
        }

        String message = "The default state (no state qualifiers) must be the last item in a "
                + "<selector>; otherwise all subsequent items are unreachable because this item "
                + "matches all states.";

        context.report(ISSUE, context.getElementLocation(element), message);
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }

        int length = attributes.getLength();
        for (int i = 0; i < length; i++) {
            Node attribute = attributes.item(i);
            if (ANDROID_URI.equals(attribute.getNamespaceURI())
                    && attribute.getLocalName() != null
                    && attribute.getLocalName().startsWith(STATE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLastElementChild(Element element) {
        Node sibling = element.getNextSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                return false;
            }
            sibling = sibling.getNextSibling();
        }
        return true;
    }
}