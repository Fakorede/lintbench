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

    // Resource types that are allowed to vary by configuration when referenced from the manifest
    private static final Collection<String> ALLOWED_VARYING_RESOURCE_TYPES =
            Arrays.asList("string", "drawable", "mipmap", "color");

    // Attributes that are allowed to reference configuration-varying resources
    private static final Collection<String> ALLOWED_ATTRIBUTES =
            Arrays.asList(
                    "label",
                    "icon",
                    "roundIcon",
                    "banner",
                    "logo",
                    "description",
                    "name");

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
        // Only check resources referenced from configuration-specific resource files
        // (i.e., files in folders with qualifiers other than just the folder type)
        String folderName = context.file.getParentFile().getName();
        if (!folderName.contains("-")) {
            // No configuration qualifiers, this is a base resource - fine
            return;
        }

        // Check if this is a resource reference
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        // Skip tools namespace attributes
        String namespaceURI = attribute.getNamespaceURI();
        if ("http://schemas.android.com/tools".equals(namespaceURI)) {
            return;
        }

        // Parse the resource reference: @[package:]type/name or @+id/name
        String resourceRef = value.substring(1); // Remove @
        if (resourceRef.startsWith("+")) {
            // @+id/... references are fine
            return;
        }

        // Remove package prefix if present
        int colonIndex = resourceRef.indexOf(':');
        if (colonIndex != -1) {
            String packageName = resourceRef.substring(0, colonIndex);
            if ("android".equals(packageName)) {
                // Android framework resources are fine
                return;
            }
            resourceRef = resourceRef.substring(colonIndex + 1);
        }

        int slashIndex = resourceRef.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String resourceType = resourceRef.substring(0, slashIndex);

        // Check if this is a resource type that can vary by configuration
        // and whether the attribute allows it
        String attributeName = attribute.getLocalName();
        if (attributeName == null) {
            attributeName = attribute.getName();
        }

        // Check if this resource type can vary across configurations
        // Resources like string, drawable, color, mipmap are configuration-dependent
        // and generally should not be referenced from configuration-qualified resource files
        // that are used in the manifest context
        if (isConfigurationDependentResourceType(resourceType)) {
            // Some attributes on application/activity elements are allowed to vary
            // (e.g., label, icon, etc.)
            Element element = attribute.getOwnerElement();
            String elementName = element != null ? element.getLocalName() : null;

            if (!isAllowedAttribute(attributeName, elementName)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        String.format(
                                "Resources referenced from the manifest cannot vary by "
                                        + "configuration (except for version qualifiers, e.g. `v21`)."
                                        + " Found resource `%1$s` in a configuration-specific "
                                        + "resource folder.",
                                value));
            }
        }
    }

    private boolean isConfigurationDependentResourceType(@NonNull String resourceType) {
        switch (resourceType) {
            case "string":
            case "drawable":
            case "color":
            case "dimen":
            case "integer":
            case "bool":
            case "array":
            case "style":
            case "layout":
            case "anim":
            case "animator":
            case "interpolator":
            case "menu":
            case "raw":
            case "xml":
                return true;
            case "mipmap":
            case "id":
            case "attr":
            case "declare-styleable":
            default:
                return false;
        }
    }

    private boolean isAllowedAttribute(@NonNull String attributeName, String elementName) {
        // The label, icon, roundIcon, banner, logo attributes on application, activity,
        // service, receiver, provider, and activity-alias elements are allowed to vary
        switch (attributeName) {
            case "label":
            case "icon":
            case "roundIcon":
            case "banner":
            case "logo":
            case "description":
                return true;
            default:
                return false;
        }
    }
}