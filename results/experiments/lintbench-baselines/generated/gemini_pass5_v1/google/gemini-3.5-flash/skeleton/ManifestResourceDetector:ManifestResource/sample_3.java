package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources "
                            + "cannot vary across configurations (except as a special case, by version, "
                            + "and except for a few specific package attributes such as the application "
                            + "title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singleton(XmlScanner.ALL);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (!value.startsWith("@")) {
            return;
        }

        if (!"AndroidManifest.xml".equals(context.file.getName())) {
            return;
        }

        String name = attribute.getLocalName();
        if (isSafeAttribute(name)) {
            return;
        }

        com.android.resources.ResourceUrl url = com.android.resources.ResourceUrl.parse(value);
        if (url == null || url.isFramework()) {
            return;
        }

        com.android.tools.lint.client.api.LintClient client = context.getClient();
        com.android.tools.lint.detector.api.Project project = context.getProject();

        com.android.resources.ResourceRepository resources = client.getProjectResources(project, true);
        if (resources == null) {
            return;
        }

        java.util.List<com.android.ide.common.resources.ResourceItem> items =
                resources.getResources(com.android.resources.ResourceNamespace.RES_AUTO, url.type, url.name);

        if (items.isEmpty()) {
            return;
        }

        for (com.android.ide.common.resources.ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config = item.getConfiguration();
            com.android.ide.common.resources.configuration.FolderConfiguration copy =
                    com.android.ide.common.resources.configuration.FolderConfiguration.copyOf(config);
            copy.setVersionQualifier(null);
            if (!copy.isDefault()) {
                String message = String.format(
                        "Value `%s` can vary by configuration (e.g. `%s`)",
                        value,
                        config.getQualifierString()
                );
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
                break;
            }
        }
    }

    private static boolean isSafeAttribute(String name) {
        return "label".equals(name)
                || "icon".equals(name)
                || "roundIcon".equals(name)
                || "theme".equals(name)
                || "logo".equals(name)
                || "banner".equals(name)
                || "description".equals(name)
                || "sharedUserLabel".equals(name);
    }
}