package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
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
        new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String aliasTypeStr = element.getAttribute("type");
        if (aliasTypeStr == null || aliasTypeStr.isEmpty()) {
            return;
        }

        Node child = element.getFirstChild();
        if (child == null || child.getNodeType() != Node.TEXT_NODE) {
            return;
        }

        String reference = child.getNodeValue().trim();
        if (!reference.startsWith("@")) {
            return;
        }

        String ref = reference.startsWith("@+") ? reference.substring(2) : reference.substring(1);
        int colon = ref.indexOf(':');
        if (colon != -1) {
            ref = ref.substring(colon + 1);
        }
        int slash = ref.indexOf('/');
        if (slash == -1) {
            return;
        }

        String refTypeStr = ref.substring(0, slash);
        ResourceType refType = ResourceType.getByName(refTypeStr);
        if (refType == null) {
            return;
        }

        ResourceType aliasType = ResourceType.getByName(aliasTypeStr);
        if (aliasType == null) {
            return;
        }

        if (aliasType != refType) {
            String message = String.format(
                "Resource alias type '%s' does not match referenced resource type '%s'",
                aliasType.getName(), refType.getName());
            context.report(ISSUE, context.getLocation(element), message);
        }
    }
}