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

    /** The current file's element tag name (resource type) being processed */
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

        // We're looking for <item> elements that reference another resource
        // e.g. <item type="color" name="foo">@drawable/bar</item>
        // where the type of the alias (color) doesn't match the type of the reference (drawable)
        if (!"item".equals(tagName)) {
            return;
        }

        String typeAttr = element.getAttribute("type");
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        String textContent = element.getTextContent();
        if (textContent == null) {
            return;
        }
        textContent = textContent.trim();

        // Check if the value is a resource reference like @type/name
        if (!textContent.startsWith("@")) {
            return;
        }

        // Strip the leading '@'
        String reference = textContent.substring(1);

        // Handle package-qualified references like @android:color/foo
        if (reference.contains(":")) {
            int colonIndex = reference.indexOf(':');
            reference = reference.substring(colonIndex + 1);
        }

        // Now reference should be like "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex < 0) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Normalize types: some aliases may use different but equivalent type names
        String normalizedAliasType = normalizeType(typeAttr);
        String normalizedReferencedType = normalizeType(referencedType);

        if (!normalizedAliasType.equals(normalizedReferencedType)) {
            // Only report when visiting the "type" attribute to avoid double-reporting
            if ("type".equals(attribute.getLocalName())) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "Wrong resource type: expected value of type `@%1$s` but "
                                        + "referenced value is of type `@%2$s`",
                                typeAttr,
                                referencedType));
            }
        }
    }

    /**
     * Normalizes resource type names to handle common aliases.
     * For example, "integer" and "int" are sometimes used interchangeably.
     */
    private static String normalizeType(@NonNull String type) {
        switch (type) {
            case "integer":
                return "integer";
            case "int":
                return "integer";
            case "colour":
                return "color";
            default:
                return type;
        }
    }
}