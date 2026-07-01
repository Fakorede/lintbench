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
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_PROVIDER;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, "
                            + "and except for a few specific package attributes such as the "
                            + "application title and icon).",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ManifestResourceDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    // Attributes that are allowed to vary across configurations in the manifest
    private static final Collection<String> ALLOWED_VARYING_ATTRS = Arrays.asList(
            ATTR_LABEL,
            ATTR_ICON,
            ATTR_THEME,
            "description",
            "banner",
            "logo",
            "roundIcon"
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML
                || folderType == ResourceFolderType.VALUES
                || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ALL_ATTRIBUTES);
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

        // Skip tools namespace
        String namespaceURI = attribute.getNamespaceURI();
        if ("http://schemas.android.com/tools".equals(namespaceURI)) {
            return;
        }

        String localName = attribute.getLocalName();
        if (localName == null) {
            localName = attribute.getName();
        }

        // Check if this is an attribute that is allowed to vary
        if (ALLOWED_VARYING_ATTRS.contains(localName)) {
            return;
        }

        // Skip android:name and android:package as they are identifiers, not resources
        if (ATTR_NAME.equals(localName) || ATTR_PACKAGE.equals(localName)) {
            return;
        }

        // Check if the resource reference uses a configuration-specific qualifier
        // by looking at the resource folder the file is in
        String resourcePath = context.getFile().getParentFile() != null
                ? context.getFile().getParentFile().getName()
                : null;

        if (resourcePath != null && resourcePath.contains("-")) {
            // This resource is in a qualified folder (e.g., values-v21, values-land, etc.)
            // Check if it's only a version qualifier (which is allowed)
            String qualifier = resourcePath.substring(resourcePath.indexOf('-') + 1);
            if (!isVersionOnlyQualifier(qualifier)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        String.format(
                                "Resources referenced from the manifest cannot vary by "
                                        + "configuration (except for version qualifiers, e.g. "
                                        + "`-v21`). Found resource `%1$s` in file which has "
                                        + "configuration-specific qualifiers.",
                                value));
            }
        }
    }

    /**
     * Returns true if the given qualifier string only contains version qualifiers (e.g., "v21").
     */
    private static boolean isVersionOnlyQualifier(@NonNull String qualifier) {
        // Split by '-' in case there are multiple qualifiers
        String[] parts = qualifier.split("-");
        for (String part : parts) {
            if (!part.matches("v\\d+")) {
                return false;
            }
        }
        return true;
    }
}