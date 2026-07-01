package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /** The current file's resource type (derived from the element tag being visited) */
    private String mCurrentType;

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
        mCurrentType = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // We are looking at <item type="..." name="...">@type/name</item> constructs
        // where the type of the reference doesn't match the declared type of the item.

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Only process <item> elements with a "type" attribute
        String tagName = element.getTagName();
        if (!"item".equals(tagName)) {
            return;
        }

        String declaredType = attribute.getValue();
        if (declaredType == null || declaredType.isEmpty()) {
            return;
        }

        // Get the text content of the element which should be a reference like @type/name
        String textContent = element.getTextContent();
        if (textContent == null) {
            return;
        }
        textContent = textContent.trim();

        // Check if the content is a resource reference
        if (!textContent.startsWith("@")) {
            return;
        }

        // Strip leading '@' and optional '+' (for new ids)
        String reference = textContent.substring(1);
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        // Handle namespace prefix like "android:"
        int slashIndex = reference.indexOf('/');
        if (slashIndex < 0) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Strip any namespace prefix (e.g. "android:color" -> "color")
        int colonIndex = referencedType.indexOf(':');
        if (colonIndex >= 0) {
            referencedType = referencedType.substring(colonIndex + 1);
        }

        // Strip any namespace prefix from declared type as well
        colonIndex = declaredType.indexOf(':');
        if (colonIndex >= 0) {
            declaredType = declaredType.substring(colonIndex + 1);
        }

        // Compare the declared type with the referenced type
        if (!declaredType.equals(referencedType)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(element),
                    String.format(
                            "Unexpected resource reference type; expected value of type `@%1$s/`"
                                    + " but value was `%2$s`",
                            declaredType,
                            textContent));
        }
    }
}