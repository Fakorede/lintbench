package com.android.tools.lint.checks;

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
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
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
        return Collections.singleton("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (parentNode == null || !"resources".equals(parentNode.getNodeName())) {
            return;
        }

        String tagName = element.getTagName();
        String resourceType;
        if ("item".equals(tagName)) {
            resourceType = element.getAttribute("type");
            if (resourceType == null || resourceType.isEmpty()) {
                return;
            }
        } else {
            resourceType = tagName;
        }

        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        if (value.startsWith("@") || value.startsWith("?")) {
            if (value.equals("@null") || value.equals("@empty")) {
                return;
            }
            String ref = value.substring(1);
            if (ref.startsWith("+")) {
                ref = ref.substring(1);
            }
            int slash = ref.indexOf('/');
            if (slash != -1) {
                String prefix = ref.substring(0, slash);
                int colon = prefix.indexOf(':');
                String referencedType = (colon != -1) ? prefix.substring(colon + 1) : prefix;

                if (!resourceType.equals(referencedType)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            String.format("The resource alias type '%s' does not match the pointed resource type '%s'", resourceType, referencedType)
                    );
                }
            }
        }
    }
}