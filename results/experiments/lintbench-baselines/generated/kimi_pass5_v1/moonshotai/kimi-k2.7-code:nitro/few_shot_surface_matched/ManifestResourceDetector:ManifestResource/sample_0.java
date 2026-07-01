package com.android.tools.lint.checks;

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
                    "Manifest resources cannot vary across configurations",
                    "Elements in the manifest can reference resources, but those resources cannot"
                            + " vary across configurations, except as a special case by version, or"
                            + " for a few specific application attributes such as the title and"
                            + " icon. Ensure that this resource does not vary with configuration.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        com.android.resources.ResourceUrl url = com.android.resources.ResourceUrl.parse(value);
        if (url == null || url.isFramework()) {
            return;
        }

        com.android.resources.ResourceType type = url.type;
        if (type == null) {
            return;
        }

        String tag = attribute.getOwnerElement().getLocalName();
        String localName = attribute.getLocalName();
        if ("application".equals(tag)
                && ("label".equals(localName) || "icon".equals(localName))) {
            return;
        }

        com.android.ide.common.resources.ResourceRepository repository =
                context.getProject().getResourceRepository();
        if (repository == null) {
            return;
        }

        java.util.List<com.android.ide.common.resources.ResourceItem> items =
                repository.getResourceItem(type, url.name);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (com.android.ide.common.resources.ResourceItem item : items) {
            java.io.File file = item.getFile();
            if (file == null) {
                continue;
            }
            java.io.File parent = file.getParentFile();
            if (parent == null) {
                continue;
            }
            String folderName = parent.getName();
            com.android.ide.common.resources.configuration.FolderConfiguration config =
                    com.android.ide.common.resources.configuration.FolderConfiguration.getConfig(
                            folderName);
            if (config != null && hasNonVersionQualifier(config)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Manifest attribute references a resource that varies by configuration (`"
                                + folderName + "`)");
                break;
            }
        }
    }

    private static boolean hasNonVersionQualifier(
            com.android.ide.common.resources.configuration.FolderConfiguration config) {
        for (com.android.ide.common.resources.configuration.ResourceQualifier qualifier :
                config.getQualifiers()) {
            if (qualifier != null
                    && !(qualifier
                            instanceof
                            com.android.ide.common.resources.configuration.VersionQualifier)) {
                return true;
            }
        }
        return false;
    }
}