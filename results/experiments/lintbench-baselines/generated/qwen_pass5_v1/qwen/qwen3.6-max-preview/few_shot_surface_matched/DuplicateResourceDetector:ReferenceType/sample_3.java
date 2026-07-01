package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("type");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        // No per-file state initialization required
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (!"item".equals(element.getTagName())) {
            return;
        }

        String aliasType = attribute.getValue();
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        String reference = element.getTextContent();
        if (reference == null) {
            return;
        }
        reference = reference.trim();

        if (reference.startsWith("@") || reference.startsWith("?")) {
            int start = 1;
            if (reference.startsWith("@+") || reference.startsWith("?+")) {
                start = 2;
            }
            int colon = reference.indexOf(':', start);
            if (colon != -1) {
                start = colon + 1;
            }
            int slash = reference.indexOf('/', start);
            if (slash > start) {
                String refType = reference.substring(start, slash);
                if (!aliasType.equals(refType)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Resource alias type mismatch: expected @" + aliasType + "/ but found @" + refType + "/");
                }
            }
        }
    }
}