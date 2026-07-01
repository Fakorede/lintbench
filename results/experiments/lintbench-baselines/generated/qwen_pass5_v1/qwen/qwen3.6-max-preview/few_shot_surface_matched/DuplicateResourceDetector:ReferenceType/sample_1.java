package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("type");
    }

    @Override
    public boolean appliesTo(@NonNull String rootTag) {
        return "resources".equals(rootTag);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        // Reserved for pre-file analysis state initialization if needed
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String aliasType = attribute.getValue();
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String textContent = element.getTextContent();
        if (textContent == null) {
            return;
        }

        String reference = textContent.trim();
        if (!reference.startsWith("@")) {
            return;
        }

        String ref = reference.substring(1);
        int colonIndex = ref.indexOf(':');
        if (colonIndex != -1) {
            ref = ref.substring(colonIndex + 1);
        }

        int slashIndex = ref.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String refType = ref.substring(0, slashIndex);
        if (refType.startsWith("+")) {
            refType = refType.substring(1);
        }

        if (!aliasType.equals(refType)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Resource alias type mismatch: expected type \"" + refType +
                    "\" but alias is defined as type \"" + aliasType + "\"");
        }
    }
}