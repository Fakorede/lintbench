package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String aliasType = element.getAttribute(ATTR_TYPE);
        if (aliasType.isEmpty()) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        String value = element.getTextContent().trim();
        if (value.isEmpty()) {
            return;
        }

        String referenceType = getReferenceType(value);
        if (referenceType == null) {
            return;
        }

        if (!aliasType.equals(referenceType)) {
            String message =
                    String.format(
                            "Expected reference type `@%1$s/...` to match alias type `%1$s`, found `@%2$s/...`",
                            aliasType, referenceType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    @Nullable
    private static String getReferenceType(@NonNull String reference) {
        if (!reference.startsWith("@")) {
            return null;
        }

        int slash = reference.indexOf('/');
        if (slash < 0) {
            return null;
        }

        String type = reference.substring(1, slash);
        int colon = type.indexOf(':');
        if (colon >= 0) {
            type = type.substring(colon + 1);
        }

        if (type.startsWith("+")) {
            type = type.substring(1);
        }

        if (type.isEmpty()) {
            return null;
        }

        return type;
    }
}