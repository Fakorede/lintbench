package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import java.util.Collection;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

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
                    new Implementation(
                            ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@") || value.startsWith("@android:") || value.startsWith("@+")) {
            return;
        }

        int slash = value.indexOf('/');
        if (slash == -1) {
            return;
        }

        String resourceType = value.substring(1, slash);
        String resourceName = value.substring(slash + 1);

        com.android.resources.ResourceType type = com.android.resources.ResourceType.fromXmlValue(resourceType);
        if (type == null) {
            return;
        }

        com.android.ide.common.resources.ResourceRepository repository = context.getProject().getResourceRepository();
        if (repository == null) {
            return;
        }

        java.util.List<com.android.ide.common.resources.ResourceItem> items =
                repository.getResources(com.android.resources.ResourceNamespace.RES_AUTO, type, resourceName);

        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }

        boolean allowsLocale = "label".equals(name) || "description".equals(name) || "title".equals(name)
                || "subtitle".equals(name) || "summary".equals(name);
        boolean allowsDensity = "icon".equals(name) || "roundIcon".equals(name) || "logo".equals(name)
                || "banner".equals(name);

        for (com.android.ide.common.resources.ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config = item.getConfiguration();
            if (!isAllowedConfig(config, allowsLocale, allowsDensity)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        String.format("The resource `%s` can vary by configurations other than what is allowed for `%s`", value, attribute.getName()));
                break;
            }
        }
    }

    private boolean isAllowedConfig(
            com.android.ide.common.resources.configuration.FolderConfiguration config,
            boolean allowsLocale,
            boolean allowsDensity) {
        for (int i = 0; i < com.android.ide.common.resources.configuration.FolderConfiguration.getQualifierCount(); i++) {
            com.android.ide.common.resources.configuration.ResourceQualifier qualifier = config.getQualifier(i);
            if (qualifier != null) {
                if (qualifier instanceof com.android.ide.common.resources.configuration.VersionQualifier) {
                    continue;
                }
                if (allowsLocale && qualifier instanceof com.android.ide.common.resources.configuration.LocaleQualifier) {
                    continue;
                }
                if (allowsDensity && qualifier instanceof com.android.ide.common.resources.configuration.DensityQualifier) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }
}