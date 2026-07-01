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

    /** The current resource type being defined in the values file */
    private String mCurrentResourceType;

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
        mCurrentResourceType = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // We are looking at <item type="..." name="...">@type/name</item> elements
        // where the referenced type must match the declared type.
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String tagName = element.getTagName();
        if (!"item".equals(tagName)) {
            return;
        }

        // Get the declared type of this alias
        String declaredType = attribute.getValue();
        if (declaredType == null || declaredType.isEmpty()) {
            return;
        }

        // Get the value (the reference)
        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        // Check if the value is a resource reference
        if (!value.startsWith("@")) {
            return;
        }

        // Strip off the optional package prefix (e.g. @android:color/foo -> color/foo)
        String reference = value.substring(1); // remove leading '@'

        // Handle theme attribute references (@*package:type/name or @package:type/name)
        if (reference.startsWith("*")) {
            reference = reference.substring(1);
        }

        // Strip package prefix if present (e.g. "android:color/foo" -> "color/foo")
        int colonIndex = reference.indexOf(':');
        if (colonIndex != -1) {
            reference = reference.substring(colonIndex + 1);
        }

        // Now reference should be "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Compare the declared type with the referenced type
        if (!declaredType.equals(referencedType)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(element),
                    String.format(
                            "Unexpected resource reference type; expected `%1$s`, got `%2$s`",
                            declaredType,
                            referencedType));
        }
    }
}