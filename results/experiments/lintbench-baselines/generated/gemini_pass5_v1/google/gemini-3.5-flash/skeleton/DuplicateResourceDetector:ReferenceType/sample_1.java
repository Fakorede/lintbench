package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("type");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String attributeName = attribute.getLocalName();
        if (attributeName == null) {
            attributeName = attribute.getName();
        }
        if (!"type".equals(attributeName)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }
        if (!"item".equals(tagName)) {
            return;
        }

        if (!element.hasAttribute("name")) {
            return;
        }

        String type = attribute.getValue();
        String text = element.getTextContent();
        if (text == null) {
            return;
        }
        text = text.trim();

        if (text.isEmpty()) {
            return;
        }

        if (text.startsWith("@") || text.startsWith("?")) {
            int slash = text.indexOf('/');
            if (slash != -1) {
                int colon = text.indexOf(':');
                int start = 1;
                if (colon != -1 && colon < slash) {
                    start = colon + 1;
                }
                String refType = text.substring(start, slash);
                if (refType.startsWith("+")) {
                    refType = refType.substring(1);
                }

                if (!type.equals(refType)) {
                    // Drawables can also accept colors or mipmaps as aliases
                    if ("drawable".equals(type) && ("color".equals(refType) || "mipmap".equals(refType))) {
                        return;
                    }

                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            String.format("Mismatch between alias type `%1$s` and referent type `%2$s`", type, refType)
                    );
                }
            }
        }
    }
}