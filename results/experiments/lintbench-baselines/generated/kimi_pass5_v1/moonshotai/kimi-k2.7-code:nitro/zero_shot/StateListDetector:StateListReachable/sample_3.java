package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a `<selector>`, only the last item should omit state qualifiers. "
                    + "If a default item (with no state attributes) appears earlier, "
                    + "all following items will be ignored.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    StateListDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        List<Element> items = new ArrayList<>();

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if ("item".equals(childElement.getLocalName())) {
                items.add(childElement);
            }
        }

        int itemCount = items.size();
        for (int i = 0; i < itemCount; i++) {
            Element item = items.get(i);
            if (!hasStateAttribute(item)) {
                if (i < itemCount - 1) {
                    context.report(
                            ISSUE,
                            item,
                            context.getLocation(item),
                            "Default `<item>` should be the last item in the selector; "
                                    + "subsequent items are unreachable."
                    );
                }
            }
        }
    }

    private static boolean hasStateAttribute(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            if (SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
                String localName = attribute.getLocalName();
                if (localName != null && localName.startsWith("state_")) {
                    return true;
                }
            }
        }
        return false;
    }
}