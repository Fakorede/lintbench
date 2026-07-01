package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class,
                            Scope.RESOURCE_FILE_SCOPE
                    )
            );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("type");
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        // No-op, required override by specification
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (!"item".equals(element.getTagName())) {
            return;
        }

        if (!element.hasAttribute("name")) {
            return;
        }

        String aliasType = attribute.getValue();
        String value = element.getTextContent().trim();

        if (value.startsWith("@")) {
            int colon = value.indexOf(':');
            int slash = value.indexOf('/');
            if (slash != -1) {
                int start = (colon != -1) ? colon + 1 : 1;
                String targetType = value.substring(start, slash);
                if (!targetType.equals(aliasType)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "The resource alias '" + element.getAttribute("name")
                                    + "' has type '" + aliasType
                                    + "' but points to a resource of type '" + targetType + "'"
                    );
                }
            }
        }
    }
}