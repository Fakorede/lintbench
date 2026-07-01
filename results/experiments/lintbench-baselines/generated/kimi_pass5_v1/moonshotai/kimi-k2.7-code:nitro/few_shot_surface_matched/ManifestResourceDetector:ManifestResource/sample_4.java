package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.ResourceRepository;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Resources in manifest cannot vary across configurations",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, "
                            + "and except for a few specific package attributes such as the "
                            + "application title and icon).",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        com.android.resources.ResourceUrl url = com.android.resources.ResourceUrl.parse(value);
        if (url == null || url.framework || url.create) {
            return;
        }

        com.android.resources.ResourceType type = url.type;
        String name = url.name;
        if (type == null || name == null || name.isEmpty()) {
            return;
        }

        org.w3c.dom.Element owner = attribute.getOwnerElement();
        if (owner != null && "application".equals(owner.getTagName())) {
            String localName = attribute.getLocalName();
            if (localName != null
                    && (localName.equals("label") || localName.contains("icon"))) {
                return;
            }
        }

        ResourceRepository resources = context.getProject().getResources();
        if (resources == null) {
            return;
        }

        java.util.List<com.android.ide.common.resources.ResourceItem> items =
                resources.getResourceItem(type, name);
        if (items == null || items.size() < 2) {
            return;
        }

        for (com.android.ide.common.resources.ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config =
                    item.getConfiguration();
            if (config == null) {
                continue;
            }
            java.util.List<com.android.resources.Qualifier> qualifiers = config.getQualifiers();
            if (qualifiers == null || qualifiers.isEmpty()) {
                continue;
            }
            for (com.android.resources.Qualifier q : qualifiers) {
                if (!(q instanceof com.android.resources.VersionQualifier)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Manifest resource references cannot vary across configurations "
                                    + "(except by version)");
                    return;
                }
            }
        }
    }
}