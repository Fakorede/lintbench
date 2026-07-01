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
        // No per-file state required
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
        aliasType = aliasType.trim();

        String reference = element.getTextContent();
        if (reference == null) {
            return;
        }
        reference = reference.trim();

        if (reference.equals("@null") || reference.isEmpty()) {
            return;
        }

        if (!reference.startsWith("@") && !reference.startsWith("?")) {
            return;
        }

        int start = 1;
        if (reference.startsWith("@+") || reference.startsWith("?+")) {
            start = 2;
        }

        int slashIndex = reference.indexOf('/', start);
        if (slashIndex == -1) {
            return;
        }

        int colonIndex = reference.indexOf(':', start);
        String refType;
        if (colonIndex != -1 && colonIndex < slashIndex) {
            refType = reference.substring(colonIndex + 1, slashIndex);
        } else {
            refType = reference.substring(start, slashIndex);
        }

        if (!aliasType.equals(refType)) {
            String message = String.format(
                    "Resource type mismatch: alias declares type `%s` but references a `%s` resource",
                    aliasType, refType);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}