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
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_DESCRIPTION;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, "
                            + "and except for a few specific package attributes such as the "
                            + "application title and icon).\n"
                            + "\n"
                            + "For example, the `allowBackup`, `exported`, `process`, "
                            + "`targetSdkVersion` and similar attributes should not be resource "
                            + "references; they need to be specified as explicit values.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ManifestResourceDetector.class,
                            Scope.MANIFEST_SCOPE));

    // Attributes that are allowed to vary (e.g. title, icon, description, theme, label)
    private static final Collection<String> ALLOWED_ATTRS = Arrays.asList(
            ATTR_ICON,
            ATTR_LABEL,
            ATTR_DESCRIPTION,
            ATTR_THEME,
            "logo",
            "banner",
            "roundIcon"
    );

    public ManifestResourceDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false; // We only care about manifest files
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        // Only care about resource references
        if (!value.startsWith(PREFIX_RESOURCE_REF) && !value.startsWith(PREFIX_THEME_REF)) {
            return;
        }

        // Skip namespace declarations
        String namespaceURI = attribute.getNamespaceURI();
        if (namespaceURI != null && namespaceURI.equals("http://www.w3.org/2000/xmlns/")) {
            return;
        }

        String attrName = attribute.getLocalName();
        if (attrName == null) {
            attrName = attribute.getName();
        }

        // Some attributes are explicitly allowed to reference resources that can vary
        if (ALLOWED_ATTRS.contains(attrName)) {
            return;
        }

        // Check if this is a resource type that can vary across configurations
        // Resources that vary across configurations (other than version) are not allowed
        // in the manifest (except for the allowed attrs above).
        // We need to check what type of resource is being referenced.
        int slashIndex = value.indexOf('/');
        if (slashIndex != -1) {
            String resourceType = value.substring(value.charAt(0) == '@' ? 1 : 1, slashIndex);
            if (resourceType.startsWith("+")) {
                resourceType = resourceType.substring(1);
            }

            // String resources and other configuration-varying resources are problematic
            // unless this is a known-safe attribute
            switch (resourceType) {
                case "string":
                case "bool":
                case "integer":
                case "color":
                case "dimen":
                case "array":
                case "plurals":
                    // These resource types can vary by configuration
                    // Check if this is a known-safe element/attribute combination
                    Element element = attribute.getOwnerElement();
                    String tagName = element.getTagName();

                    // For application-level elements, label and icon are allowed
                    // For other resource references that can vary, report an issue
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            String.format(
                                    "Resources referenced from the manifest cannot vary by "
                                            + "configuration (except for version qualifiers, "
                                            + "e.g. `-%1$s`). Found `%2$s` inside `%3$s`",
                                    "v21",
                                    value,
                                    tagName));
                    break;
                default:
                    // drawable, mipmap, etc. are generally OK for icons
                    break;
            }
        }
    }
}