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
                    "When you generate a resource alias, the resource you are pointing to must be " +
                    "of the same type as the alias. For example, an <item type=\"drawable\"> must " +
                    "reference a @drawable/ resource.",
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
        // No state to initialize or reset
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (!"item".equals(element.getTagName())) {
            return;
        }

        String aliasType = attribute.getValue();
        if (aliasType == null) {
            return;
        }
        aliasType = aliasType.trim();
        if (aliasType.isEmpty()) {
            return;
        }

        String reference = element.getTextContent();
        if (reference == null) {
            return;
        }
        reference = reference.trim();

        String refType = getReferenceType(reference);
        if (refType != null && !aliasType.equals(refType)) {
            String message = String.format(
                    "Resource alias type '%s' does not match referenced resource type '%s'",
                    aliasType, refType);
            context.report(ISSUE, context.getLocation(attribute), message);
        }
    }

    private static String getReferenceType(String reference) {
        if (reference == null || !reference.startsWith("@")) {
            return null;
        }
        String s = reference.substring(1);
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        int colon = s.indexOf(':');
        if (colon != -1) {
            s = s.substring(colon + 1);
        }
        int slash = s.indexOf('/');
        if (slash != -1) {
            return s.substring(0, slash);
        }
        return null;
    }
}