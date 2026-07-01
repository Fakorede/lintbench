package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be " +
            "of the same type as the alias",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        if (!element.hasAttribute("type")) {
            return;
        }

        String aliasType = element.getAttribute("type");
        if (aliasType.isEmpty()) {
            return;
        }

        String text = element.getTextContent();
        if (text == null) {
            return;
        }
        text = text.trim();

        String referredType = getReferredType(text);
        if (referredType == null) {
            return;
        }

        if (!aliasType.equals(referredType)) {
            if ("drawable".equals(aliasType) && "color".equals(referredType)) {
                return;
            }

            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format("Resource alias '%s' of type '%s' cannot point to a resource of type '%s'",
                            element.getAttribute("name"), aliasType, referredType)
            );
        }
    }

    private static String getReferredType(String val) {
        if (val == null || !val.startsWith("@") || val.startsWith("@+")) {
            return null;
        }
        int slash = val.indexOf('/');
        if (slash == -1) {
            return null;
        }
        int colon = val.indexOf(':');
        int start = 1;
        if (colon != -1 && colon < slash) {
            start = colon + 1;
        }
        return val.substring(start, slash);
    }
}