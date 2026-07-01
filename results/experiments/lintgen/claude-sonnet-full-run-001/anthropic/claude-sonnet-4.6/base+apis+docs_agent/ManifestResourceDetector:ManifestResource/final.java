package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks for resource references in the manifest that cannot vary by configuration.
 */
public class ManifestResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(
                    ManifestResourceDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    /**
     * Attributes in the manifest that ARE allowed to reference configuration-varying resources.
     * These are typically the application label, icon, banner, logo, etc.
     */
    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            SdkConstants.ATTR_LABEL,
            SdkConstants.ATTR_ICON,
            "banner",
            "logo",
            "description",
            SdkConstants.ATTR_ROUND_ICON,
            "smallIcon",
            "taskDescription"
    ));

    /**
     * Resource types that should NOT vary by configuration in the manifest.
     */
    private static final Set<String> DISALLOWED_VARYING_TYPES = new HashSet<>(Arrays.asList(
            "layout",
            "menu",
            "anim",
            "animator",
            "interpolator",
            "transition",
            "raw",
            "font",
            "navigation",
            "array",
            "plurals",
            "dimen",
            "fraction",
            "integer",
            "bool",
            "id",
            "attr"
    ));

    public ManifestResourceDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return false; // We handle manifest separately
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Only check the manifest file
        if (!context.document.getDocumentElement().getTagName().equals(SdkConstants.TAG_MANIFEST)) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Node node = attributes.item(i);
            if (!(node instanceof Attr)) {
                continue;
            }
            Attr attr = (Attr) node;
            String value = attr.getValue();
            if (value == null || !value.startsWith("@")) {
                continue;
            }

            // Skip tools namespace attributes
            String namespaceURI = attr.getNamespaceURI();
            if (SdkConstants.TOOLS_URI.equals(namespaceURI)) {
                continue;
            }

            // Parse the resource reference: @[+][package:]type/name
            String reference = value;
            if (reference.startsWith("@+")) {
                reference = reference.substring(2);
            } else if (reference.startsWith("@")) {
                reference = reference.substring(1);
            }

            // Remove package prefix if present
            int colonIndex = reference.indexOf(':');
            String resourceType;
            String resourceName;
            if (colonIndex != -1) {
                String pkg = reference.substring(0, colonIndex);
                String afterColon = reference.substring(colonIndex + 1);
                int slashIndex = afterColon.indexOf('/');
                if (slashIndex == -1) {
                    continue;
                }
                resourceType = afterColon.substring(0, slashIndex);
                resourceName = afterColon.substring(slashIndex + 1);

                // If it's an android: resource, it's fine - it's a framework resource
                if ("android".equals(pkg)) {
                    continue;
                }
            } else {
                int slashIndex = reference.indexOf('/');
                if (slashIndex == -1) {
                    continue;
                }
                resourceType = reference.substring(0, slashIndex);
                resourceName = reference.substring(slashIndex + 1);
            }

            if (resourceType.isEmpty() || resourceName.isEmpty()) {
                continue;
            }

            // Check if this attribute is allowed to reference varying resources
            String attrLocalName = attr.getLocalName();
            if (attrLocalName == null) {
                attrLocalName = attr.getName();
                int colonIdx = attrLocalName.indexOf(':');
                if (colonIdx != -1) {
                    attrLocalName = attrLocalName.substring(colonIdx + 1);
                }
            }

            // Allowed attributes (like label, icon) can reference any resource type
            if (ALLOWED_ATTRIBUTES.contains(attrLocalName)) {
                continue;
            }

            // For other attributes, check if the resource type is one that can vary
            // by configuration in problematic ways
            if (isProblematicResourceType(resourceType)) {
                reportIssue(context, attr, resourceType, resourceName);
            }
        }
    }

    private boolean isProblematicResourceType(String resourceType) {
        return DISALLOWED_VARYING_TYPES.contains(resourceType);
    }

    private void reportIssue(XmlContext context, Attr attr, String resourceType, String resourceName) {
        String message = String.format(
                "Resources referenced from the manifest cannot vary by configuration " +
                "(except for version qualifiers, e.g. `-v21`). Found `@%1$s/%2$s` in attribute `%3$s`",
                resourceType, resourceName, attr.getName());

        context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                message
        );
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // We handle everything in visitElement
    }
}