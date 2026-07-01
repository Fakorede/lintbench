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
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
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

        String type = element.getAttribute("type");
        if (type == null || type.isEmpty()) {
            return;
        }

        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        if (!value.startsWith("@") && !value.startsWith("?")) {
            return;
        }

        if (value.equals("@null")) {
            return;
        }

        String refType = null;
        int slash = value.indexOf('/');
        if (slash != -1) {
            int start = 1;
            if (value.startsWith("@+") && value.length() > 2) {
                start = 2;
            }
            String prefix = value.substring(start, slash);
            int colon = prefix.indexOf(':');
            if (colon != -1) {
                refType = prefix.substring(colon + 1);
            } else {
                refType = prefix;
            }
        } else {
            if (value.startsWith("?")) {
                refType = "attr";
            }
        }

        if (refType != null && !refType.equals(type)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format("Resource alias '%s' of type '%s' expects a reference of type '%s', but got '%s'",
                            element.getAttribute("name"), type, type, refType)
            );
        }
    }
}