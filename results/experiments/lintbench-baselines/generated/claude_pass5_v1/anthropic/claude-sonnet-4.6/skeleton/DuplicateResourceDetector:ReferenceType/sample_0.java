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

    /** The current element tag (resource type) being processed */
    private String mCurrentTag;

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
        mCurrentTag = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // We're looking at <item type="..." name="...">@type/name</item> patterns
        // where the type attribute tells us what kind of resource this is,
        // and the value should reference a resource of the same type.

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Only process <item> elements that have a type attribute
        String tagName = element.getTagName();
        if (!"item".equals(tagName)) {
            return;
        }

        // Get the declared type of this resource alias
        String declaredType = attribute.getValue();
        if (declaredType == null || declaredType.isEmpty()) {
            return;
        }

        // Get the text content of the element - this should be the reference
        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        // Check if the value is a resource reference (starts with @)
        if (!value.startsWith("@")) {
            return;
        }

        // Remove the @ prefix
        String reference = value.substring(1);

        // Handle @+type/name and @type/name
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        // Split into type and name parts
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Handle namespace prefixes like android:color -> color
        int colonIndex = referencedType.indexOf(':');
        if (colonIndex != -1) {
            referencedType = referencedType.substring(colonIndex + 1);
        }

        // Check if the referenced type matches the declared type
        if (!referencedType.isEmpty() && !referencedType.equals(declaredType)) {
            String message =
                    String.format(
                            "Unexpected resource reference type; expected value of type `@%1$s/`",
                            declaredType);
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    message);
        }
    }
}