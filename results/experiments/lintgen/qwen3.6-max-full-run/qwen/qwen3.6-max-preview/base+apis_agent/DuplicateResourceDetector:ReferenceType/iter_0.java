package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String typeAttr = element.getAttribute("type");
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        NodeList children = element.getChildNodes();
        String text = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                text = child.getNodeValue().trim();
                break;
            }
        }

        if (text == null || !text.startsWith("@") || text.startsWith("@+")) {
            return;
        }

        String ref = text.substring(1);
        int colon = ref.indexOf(':');
        if (colon != -1) {
            ref = ref.substring(colon + 1);
        }
        int slash = ref.indexOf('/');
        if (slash == -1) {
            return;
        }

        String refType = ref.substring(0, slash);
        if (!typeAttr.equals(refType)) {
            String message = String.format(
                    "Resource alias type mismatch: expected `%1$s` but referenced `%2$s`",
                    typeAttr, refType);
            context.report(ISSUE, context.getLocation(element), message);
        }
    }
}