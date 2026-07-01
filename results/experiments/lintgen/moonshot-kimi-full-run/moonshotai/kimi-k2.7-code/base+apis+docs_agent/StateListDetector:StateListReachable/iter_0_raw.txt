package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;

public class StateListDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in selector",
            "In a `<selector>`, only the last `<item>` should omit a state qualifier. "
                    + "If a default item with no state attributes appears earlier, "
                    + "all subsequent items will be ignored because the default item matches all states.",
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
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        int childCount = children.getLength();

        for (int i = 0; i < childCount; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !"item".equals(child.getLocalName())) {
                continue;
            }

            // Only the last item may be a default (no state qualifiers).
            if (i < childCount - 1 && !hasStateAttributes((Element) child)) {
                context.report(
                        ISSUE,
                        (Element) child,
                        context.getLocation((Element) child),
                        "Default item is not the last element in the selector; "
                                + "subsequent items will never be matched"
                );
            }
        }
    }

    private static boolean hasStateAttributes(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        int length = attributes.getLength();
        for (int i = 0; i < length; i++) {
            Node attr = attributes.item(i);
            String name = attr.getLocalName();
            String namespace = attr.getNamespaceURI();
            if (name != null
                    && SdkConstants.ANDROID_URI.equals(namespace)
                    && name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}