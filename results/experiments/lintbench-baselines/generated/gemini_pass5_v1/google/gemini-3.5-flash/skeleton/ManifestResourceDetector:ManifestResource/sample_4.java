package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, and "
                            + "except for a few specific package attributes such as the application "
                            + "title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singleton("*");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (!value.startsWith("@") || value.startsWith("@android:")) {
            return;
        }

        String name = attribute.getLocalName();
        String uri = attribute.getNamespaceURI();
        if ("http://schemas.android.com/apk/res/android".equals(uri)) {
            if ("icon".equals(name)
                    || "roundIcon".equals(name)
                    || "label".equals(name)
                    || "logo".equals(name)
                    || "banner".equals(name)
                    || "description".equals(name)
                    || "sharedUserLabel".equals(name)) {
                return;
            }
        }

        com.android.resources.ResourceUrl url = com.android.resources.ResourceUrl.parse(value);
        if (url == null || url.isFramework()) {
            return;
        }

        com.android.tools.lint.client.api.LintClient client = context.getClient();
        com.android.tools.lint.detector.api.Project project = context.getProject();
        com.android.ide.common.resources.ResourceRepository resources = client.getResourceRepository(project, true, false);
        if (resources == null) {
            return;
        }

        List<com.android.ide.common.resources.ResourceItem> items = resources.getResources(url.getNamespace(), url.type, url.name);
        if (items.isEmpty()) {
            return;
        }

        for (com.android.ide.common.resources.ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config = item.getConfiguration();
            if (config.isDefault()) {
                continue;
            }

            // Version qualifier is allowed
            if (config.getVersionQualifier() != null) {
                // Check if any other qualifiers are set
                com.android.ide.common.resources.configuration.FolderConfiguration copy =
                        com.android.ide.common.resources.configuration.FolderConfiguration.copyOf(config);
                copy.setVersionQualifier(null);
                if (copy.isDefault()) {
                    continue;
                }
            }

            String message = String.format(
                    "Resource `%s` can vary by configuration (for example %s)",
                    value,
                    item.getConfiguration().getQualifierString());
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
            break;
        }
    }
}