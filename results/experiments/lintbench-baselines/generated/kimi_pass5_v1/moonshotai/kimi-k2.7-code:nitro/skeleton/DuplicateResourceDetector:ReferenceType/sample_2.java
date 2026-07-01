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
                    "When creating a resource alias, the reference it points to must be of the "
                            + "same type as the alias. For example, a drawable alias must use "
                            + "`@drawable/...`, a color alias must use `@color/...`, and so on.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singleton("name");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file state is required for this check.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"name".equals(attribute.getName())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String tag = element.getTagName();
        String aliasType;
        if ("item".equals(tag)) {
            aliasType = element.getAttribute("type");
            if (aliasType == null || aliasType.isEmpty()) {
                return;
            }
        } else {
            aliasType = tag;
        }

        String reference = element.getTextContent();
        if (reference == null) {
            return;
        }
        reference = reference.trim();
        if (reference.isEmpty() || !reference.startsWith("@")) {
            return;
        }

        String refType = getReferenceType(reference);
        if (refType == null) {
            return;
        }

        if (!aliasType.equalsIgnoreCase(refType)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "Mismatched resource type: the alias is declared as `%1$s`, but the "
                                    + "reference `%2$s` points to a `%3$s` resource",
                            aliasType, reference, refType));
        }
    }

    private static String getReferenceType(String reference) {
        int index = 0;
        if (reference.startsWith("@+")) {
            index = 2;
        } else if (reference.startsWith("@")) {
            index = 1;
        } else {
            return null;
        }

        String remainder = reference.substring(index);
        if (remainder.startsWith("*")) {
            remainder = remainder.substring(1);
        }

        int colon = remainder.indexOf(':');
        if (colon != -1) {
            remainder = remainder.substring(colon + 1);
        }

        int slash = remainder.indexOf('/');
        if (slash <= 0) {
            return null;
        }

        return remainder.substring(0, slash);
    }
}