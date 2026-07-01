package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, and except "
                            + "for a few specific package attributes such as the application title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    // Attributes in the manifest that are allowed to reference resources that vary by configuration
    private static final Collection<String> ALLOWED_VARYING_ATTRIBUTES =
            Arrays.asList(
                    "label",
                    "icon",
                    "roundIcon",
                    "banner",
                    "logo",
                    "description",
                    "theme");

    // Resource types that are NOT allowed to vary by configuration in the manifest
    private static final Collection<String> DISALLOWED_RESOURCE_TYPES =
            Arrays.asList(
                    "bool",
                    "color",
                    "dimen",
                    "id",
                    "integer",
                    "layout",
                    "plurals",
                    "string-array",
                    "typed-array");

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only check files in the manifest folder or files named AndroidManifest.xml
        // We check resource files that are referenced from the manifest
        // Actually this detector checks resource files to see if they vary by configuration
        // when referenced from the manifest.

        // Check if we're in a resource file that might be referenced from a manifest
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        // Parse the resource reference: @type/name or @+type/name
        String resourceRef = value.startsWith("@+") ? value.substring(2) : value.substring(1);
        int slashIndex = resourceRef.indexOf('/');
        if (slashIndex < 0) {
            return;
        }

        String resourceType = resourceRef.substring(0, slashIndex);
        if (resourceType.isEmpty()) {
            return;
        }

        // Check if this attribute is one of the allowed ones that can vary
        String attributeName = attribute.getLocalName();
        if (attributeName == null) {
            attributeName = attribute.getName();
        }

        // If the attribute is in the allowed list (label, icon, etc.), skip it
        if (ALLOWED_VARYING_ATTRIBUTES.contains(attributeName)) {
            return;
        }

        // Check if the resource type is one that cannot vary by configuration
        if (DISALLOWED_RESOURCE_TYPES.contains(resourceType)) {
            // Check if the resource folder has configuration qualifiers
            String folderName = context.file.getParentFile() != null
                    ? context.file.getParentFile().getName()
                    : "";

            if (hasConfigurationQualifiers(folderName)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        String.format(
                                "Resources referenced from the manifest cannot vary by "
                                        + "configuration (except for version qualifiers, e.g. `-v21`). "
                                        + "Found resource `%1$s` in folder `%2$s`",
                                value,
                                folderName));
            }
        }
    }

    /**
     * Returns true if the given folder name has configuration qualifiers (other than version
     * qualifiers like -v21).
     */
    private static boolean hasConfigurationQualifiers(@NonNull String folderName) {
        int dashIndex = folderName.indexOf('-');
        if (dashIndex < 0) {
            // No qualifiers at all
            return false;
        }

        // Extract qualifiers
        String qualifiers = folderName.substring(dashIndex + 1);
        String[] parts = qualifiers.split("-");

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            // Version qualifiers like v21 are allowed
            if (part.matches("v\\d+")) {
                continue;
            }

            // Any other qualifier means the resource varies by configuration
            return true;
        }

        return false;
    }
}