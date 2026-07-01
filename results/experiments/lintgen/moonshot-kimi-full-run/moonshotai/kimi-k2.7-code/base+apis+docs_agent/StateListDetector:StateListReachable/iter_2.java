package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        List<Element> items = new ArrayList<>();
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getLocalName())) {
                items.add((Element) child);
            }
            child = child.getNextSibling();
        }

        int itemCount = items.size();
        for (int i = 0; i < itemCount; i++) {
            if (i < itemCount - 1 && !hasStateAttributes(items.get(i))) {
                context.report(
                        ISSUE,
                        items.get(i),
                        context.getLocation(items.get(i)),
                        "This default item is not the last element in the selector; "
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