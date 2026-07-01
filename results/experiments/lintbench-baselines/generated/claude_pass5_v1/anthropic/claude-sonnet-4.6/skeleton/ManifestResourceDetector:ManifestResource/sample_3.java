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
import java.util.HashSet;
import java.util.Set;
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

    /**
     * Attributes in the manifest that are allowed to reference configuration-varying resources
     * (e.g., strings, drawables that can vary by locale or density).
     */
    private static final Set<String> ALLOWED_VARYING_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            "label",
                            "icon",
                            "roundIcon",
                            "banner",
                            "logo",
                            "description",
                            "theme"));

    /**
     * Resource types that are NOT allowed to vary across configurations in the manifest.
     * Essentially, only certain resource types like string (for label) and drawable (for icon)
     * are allowed for specific attributes. Other resource types like layout, color (in some cases),
     * etc. are not allowed to vary.
     */
    private static final Set<String> CONFIGURATION_VARYING_RESOURCE_TYPES =
            new HashSet<>(
                    Arrays.asList(
                            "layout",
                            "menu",
                            "anim",
                            "animator",
                            "interpolator",
                            "transition",
                            "xml",
                            "raw",
                            "font"));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        // This detector applies to all resource files, but we specifically want
        // to check resources that are referenced from manifests.
        // However, since this is a ResourceXmlDetector checking resource files
        // (not the manifest itself), we check resource files to see if they
        // have configuration qualifiers that would make them vary.
        // Actually, this detector checks resource XML files for issues
        // related to manifest references.
        return true;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        // Return ALL_ATTRIBUTES to check every attribute in resource files
        // that might be referenced from the manifest.
        // We want to look at attributes that contain resource references (@resource/name)
        return ALL_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Check if we are in a resource file that has configuration qualifiers
        // If this resource file is in a configuration-specific folder (e.g., values-land,
        // values-port, layout-large, etc.) and it's referenced from the manifest,
        // then we should flag it.

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        // Check if the resource folder has configuration qualifiers that would
        // make the resource vary across configurations (other than version qualifiers).
        String folderName = context.file.getParentFile().getName();
        if (!hasNonVersionQualifier(folderName)) {
            return;
        }

        // Get the attribute value and check if it's a resource reference
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        // Check if this resource type is one that cannot vary across configurations
        // when referenced from a manifest.
        String resourceType = getResourceType(value);
        if (resourceType == null) {
            return;
        }

        // Report the issue: this resource varies across configurations and
        // may be referenced from the manifest where such variation is not allowed.
        // We only flag resource types that are problematic.
        if (CONFIGURATION_VARYING_RESOURCE_TYPES.contains(resourceType)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Resources referenced from the manifest cannot vary by configuration "
                            + "(except for version qualifiers, e.g. `v21`)");
        }
    }

    /**
     * Checks if the folder name has any qualifier other than a version qualifier (vNN).
     *
     * @param folderName the resource folder name (e.g., "values-land", "values-v21")
     * @return true if there are non-version qualifiers present
     */
    private static boolean hasNonVersionQualifier(@NonNull String folderName) {
        int dashIndex = folderName.indexOf('-');
        if (dashIndex == -1) {
            // No qualifiers at all (e.g., "values", "layout")
            return false;
        }

        String qualifiers = folderName.substring(dashIndex + 1);
        String[] parts = qualifiers.split("-");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            // Version qualifiers are of the form vNN (e.g., v21, v14)
            if (part.matches("v\\d+")) {
                continue;
            }
            // This is a non-version qualifier
            return true;
        }

        return false;
    }

    /**
     * Extracts the resource type from a resource reference string.
     *
     * @param resourceRef the resource reference (e.g., "@layout/main", "@string/app_name")
     * @return the resource type, or null if it cannot be determined
     */
    private static String getResourceType(@NonNull String resourceRef) {
        // Resource references can be:
        // @type/name
        // @package:type/name
        // @+id/name
        String ref = resourceRef;
        if (ref.startsWith("@+")) {
            ref = ref.substring(2);
        } else if (ref.startsWith("@")) {
            ref = ref.substring(1);
        } else {
            return null;
        }

        // Strip package prefix if present
        int colonIndex = ref.indexOf(':');
        if (colonIndex != -1) {
            ref = ref.substring(colonIndex + 1);
        }

        int slashIndex = ref.indexOf('/');
        if (slashIndex == -1) {
            return null;
        }

        return ref.substring(0, slashIndex);
    }
}