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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /** The current element's tag name (resource type) being processed */
    private String mCurrentTag;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("name", "type");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCurrentTag = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String tagName = element.getTagName();

        // We're interested in <item> elements that define resource aliases
        if (!"item".equals(tagName)) {
            return;
        }

        // Get the "type" attribute which declares what resource type this alias is
        String typeAttr = element.getAttribute("type");
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        // Get the text content of the element (the reference value)
        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        // Check if it's a reference (starts with @)
        if (!value.startsWith("@")) {
            return;
        }

        // Parse the reference type from the value, e.g. @drawable/foo -> drawable
        // Handle optional package prefix: @android:drawable/foo -> drawable
        String referenceType = extractReferenceType(value);
        if (referenceType == null) {
            return;
        }

        // Compare the declared type with the referenced resource type
        if (!typeAttr.equals(referenceType)) {
            String message =
                    String.format(
                            "Wrong resource type: alias is of type `%1$s` but the referenced "
                                    + "resource is of type `%2$s`",
                            typeAttr, referenceType);
            context.report(ISSUE, attribute, context.getLocation(element), message);
        }
    }

    /**
     * Extracts the resource type from a reference string like {@code @drawable/foo},
     * {@code @android:drawable/foo}, or {@code @+id/foo}.
     *
     * @param reference the reference string
     * @return the resource type, or null if it cannot be determined
     */
    private static String extractReferenceType(@NonNull String reference) {
        if (!reference.startsWith("@")) {
            return null;
        }

        // Strip leading '@'
        String remainder = reference.substring(1);

        // Strip leading '+' for new IDs like @+id/foo
        if (remainder.startsWith("+")) {
            remainder = remainder.substring(1);
        }

        // Strip package prefix if present (e.g. "android:")
        int colonIndex = remainder.indexOf(':');
        if (colonIndex >= 0) {
            remainder = remainder.substring(colonIndex + 1);
        }

        // Now remainder should be "type/name"
        int slashIndex = remainder.indexOf('/');
        if (slashIndex < 0) {
            return null;
        }

        return remainder.substring(0, slashIndex);
    }
}