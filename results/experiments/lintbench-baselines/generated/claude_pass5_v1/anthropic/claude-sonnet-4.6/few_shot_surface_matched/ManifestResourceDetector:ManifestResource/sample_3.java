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
import static com.android.SdkConstants.ATTR_LOGO;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.ATTR_ROUND_ICON;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_MANIFEST;

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
                            + "For example, the `android:label` attribute on an `<activity>` "
                            + "element can reference a string resource, but that string resource "
                            + "**cannot** have different translations (it will not vary by "
                            + "language). Similarly, the manifest cannot reference resources that "
                            + "vary by screen size, density, etc.\n"
                            + "\n"
                            + "Note that there are a few exceptions:\n"
                            + "* `android:label`, `android:icon`, `android:logo`, and "
                            + "`android:roundIcon` on `<application>` are allowed to vary\n"
                            + "* Version-qualifier resources are allowed anywhere",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ManifestResourceDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES)));

    /** Attributes allowed to vary on the application element */
    private static final Collection<String> APPLICATION_ALLOWED_ATTRS =
            Arrays.asList(
                    ATTR_ICON,
                    ATTR_LABEL,
                    ATTR_LOGO,
                    ATTR_ROUND_ICON,
                    ATTR_THEME,
                    ATTR_NAME,
                    ATTR_PACKAGE);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only check manifest files
        if (!context.getFile().getName().equals("AndroidManifest.xml")) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        // Skip tools namespace attributes
        String namespaceURI = attribute.getNamespaceURI();
        if (namespaceURI != null && namespaceURI.equals("http://schemas.android.com/tools")) {
            return;
        }

        // Skip @+id and @id references - those are fine
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            return;
        }

        // Check if this is a resource reference that could vary by configuration
        // Parse the resource type from the reference
        String resourceRef = value.substring(1); // strip the '@'
        if (resourceRef.startsWith("+")) {
            resourceRef = resourceRef.substring(1);
        }

        String resourceType;
        int slashIndex = resourceRef.indexOf('/');
        if (slashIndex == -1) {
            return;
        }
        resourceType = resourceRef.substring(0, slashIndex);

        // Strip package prefix if present (e.g., "android:string/foo" -> "string")
        int colonIndex = resourceType.indexOf(':');
        if (colonIndex != -1) {
            resourceType = resourceType.substring(colonIndex + 1);
        }

        // Some resource types cannot vary by configuration in manifests
        // Check if this resource type can vary across configurations
        if (!canVaryAcrossConfigurations(resourceType)) {
            return;
        }

        // Check the element this attribute belongs to
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // The manifest and application elements have some allowed attributes
        if (TAG_APPLICATION.equals(tagName)) {
            String attrLocalName = attribute.getLocalName();
            if (APPLICATION_ALLOWED_ATTRS.contains(attrLocalName)) {
                return;
            }
        }

        if (TAG_MANIFEST.equals(tagName)) {
            return;
        }

        // Check if the resource reference uses a version qualifier (v<N>) 
        // which is allowed as a special case - we can't easily check this here
        // without resolving the resource, so we report it and let the user verify.

        // Report the issue
        String message =
                String.format(
                        "Resources referenced from the manifest cannot vary by configuration "
                                + "(except for version qualifiers, e.g. `v21`). Found `%1$s` "
                                + "from attribute `%2$s` in element `<%3$s>`",
                        value,
                        attribute.getLocalName(),
                        tagName);

        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }

    /**
     * Returns true if the given resource type can vary across configurations
     * (and thus should be flagged if used in the manifest).
     */
    private static boolean canVaryAcrossConfigurations(@NonNull String resourceType) {
        switch (resourceType) {
            // These types can vary by configuration (locale, density, size, etc.)
            case "string":
            case "dimen":
            case "bool":
            case "integer":
            case "color":
            case "drawable":
            case "mipmap":
            case "layout":
            case "array":
            case "plurals":
            case "style":
            case "fraction":
                return true;

            // These types generally do not vary
            case "id":
            case "attr":
            case "declare-styleable":
            case "raw":
            case "xml":
            case "font":
            case "menu":
            case "navigation":
            case "anim":
            case "animator":
            case "interpolator":
            case "transition":
                return false;

            default:
                return true;
        }
    }
}