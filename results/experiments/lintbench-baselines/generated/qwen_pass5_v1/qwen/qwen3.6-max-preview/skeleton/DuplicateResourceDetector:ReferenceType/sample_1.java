package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
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
        // No per-file state to initialize or reset
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null || !"item".equals(element.getTagName())) {
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
        if (!reference.startsWith("@")) {
            return;
        }

        String refType = extractResourceType(reference);
        if (refType != null && !refType.equals(aliasType)) {
            String message = String.format(
                    "Resource alias type mismatch: expected `%s` but referenced `%s`",
                    aliasType, refType);
            context.report(ISSUE, context.getLocation(attribute), message);
        }
    }

    private static String extractResourceType(@NonNull String reference) {
        // Expected format: @[+][package:]type/name
        String s = reference.substring(1); // skip '@'
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        int colonIndex = s.indexOf(':');
        if (colonIndex != -1) {
            s = s.substring(colonIndex + 1);
        }
        int slashIndex = s.indexOf('/');
        if (slashIndex != -1) {
            return s.substring(0, slashIndex);
        }
        return null;
    }
}