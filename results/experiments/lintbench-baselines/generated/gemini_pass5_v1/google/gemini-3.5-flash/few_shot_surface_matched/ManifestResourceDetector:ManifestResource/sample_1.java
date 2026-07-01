package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

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
                    new Implementation(
                            ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (!value.startsWith("@") || value.startsWith("@+")) {
            return;
        }

        String name = attribute.getLocalName();
        if ("label".equals(name)
                || "icon".equals(name)
                || "roundIcon".equals(name)
                || "theme".equals(name)
                || "description".equals(name)
                || "banner".equals(name)
                || "logo".equals(name)
                || "sharedUserLabel".equals(name)) {
            return;
        }

        String ns = attribute.getNamespaceURI();
        if ("http://schemas.android.com/tools".equals(ns)) {
            return;
        }

        com.android.resources.ResourceUrl url = com.android.resources.ResourceUrl.parse(value);
        if (url == null || url.isFramework()) {
            return;
        }

        com.android.ide.common.resources.ResourceRepository repository =
                context.getClient().getResourceRepository(context.getProject(), true, false);
        if (repository == null) {
            return;
        }

        com.android.resources.ResourceNamespace namespace;
        if (url.namespace == null) {
            namespace = com.android.resources.ResourceNamespace.RES_AUTO;
        } else {
            namespace = com.android.resources.ResourceNamespace.fromPackageName(url.namespace);
        }

        com.android.resources.ResourceType type = url.type;
        if (type == null) {
            return;
        }

        java.util.List<com.android.ide.common.resources.ResourceItem> items =
                repository.getResources(namespace, type, url.name);

        for (com.android.ide.common.resources.ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config =
                    item.getConfiguration();
            if (config != null && !config.isDefault()) {
                for (int i = 0; i < com.android.ide.common.resources.configuration.FolderConfiguration.getQualifierCount(); i++) {
                    com.android.ide.common.resources.configuration.ResourceQualifier qualifier =
                            config.getQualifier(i);
                    if (qualifier != null
                            && !(qualifier instanceof com.android.ide.common.resources.configuration.VersionQualifier)) {
                        context.report(
                                ISSUE,
                                attribute,
                                context.getLocation(attribute),
                                "The resource `"
                                        + value
                                        + "` value varies by configuration, but the manifest attribute `"
                                        + name
                                        + "` does not support varying values");
                        return;
                    }
                }
            }
        }
    }
}