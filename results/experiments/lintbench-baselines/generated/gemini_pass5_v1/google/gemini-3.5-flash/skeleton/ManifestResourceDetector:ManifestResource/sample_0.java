package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

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
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith("@") && !value.startsWith("@android:")) {
            com.android.resources.ResourceUrl url = com.android.resources.ResourceUrl.parse(value);
            if (url != null && !url.isFramework()) {
                if (isAllowedAttribute(attribute)) {
                    return;
                }
                com.android.ide.common.resources.ResourceRepository repository =
                        context.getClient().getResourceRepository(context.getProject(), true, false);
                if (repository != null) {
                    java.util.List<com.android.ide.common.resources.ResourceItem> items =
                            repository.getResources(
                                    com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO,
                                    url.type,
                                    url.name);
                    for (com.android.ide.common.resources.ResourceItem item : items) {
                        com.android.ide.common.resources.configuration.FolderConfiguration config =
                                item.getConfiguration();
                        if (config == null || config.isDefault()) {
                            continue;
                        }
                        com.android.ide.common.resources.configuration.FolderConfiguration copy =
                                new com.android.ide.common.resources.configuration.FolderConfiguration();
                        copy.setVersionQualifier(config.getVersionQualifier());
                        if (config.equals(copy)) {
                            continue;
                        }

                        context.report(
                                ISSUE,
                                attribute,
                                context.getValueLocation(attribute),
                                "Resources referenced from the manifest cannot vary by configuration "
                                        + "(except for version qualifiers, and screen densities for icons)");
                        break;
                    }
                }
            }
        }
    }

    private static boolean isAllowedAttribute(@NonNull Attr attribute) {
        String name = attribute.getLocalName();
        String namespace = attribute.getNamespaceURI();
        if (!"http://schemas.android.com/apk/res/android".equals(namespace)) {
            return false;
        }
        return "icon".equals(name)
                || "roundIcon".equals(name)
                || "label".equals(name)
                || "logo".equals(name)
                || "banner".equals(name)
                || "description".equals(name)
                || "theme".equals(name);
    }
}