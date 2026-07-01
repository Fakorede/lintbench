package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.List;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Resources referenced from the manifest cannot vary across configurations "
                            + "(except by version). The application icon and label are exempt.",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        if (isExemptApplicationAttribute(attribute)) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.theme || url.create) {
            return;
        }

        ResourceRepository repository =
                context.getDriver().getClient().getResources(context.getProject(), true);
        if (repository == null) {
            return;
        }

        List<ResourceItem> items = repository.getResourceItem(url.type, url.name);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            if (config == null || config.isDefault()) {
                continue;
            }

            if (hasOnlyVersionQualifier(config)) {
                continue;
            }

            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "The resource `%1$s` is defined in configuration `%2$s`; resources "
                                    + "referenced from the manifest cannot vary across "
                                    + "configurations (except by version)",
                            value,
                            config.getQualifiersString()));
            break;
        }
    }

    private static boolean isExemptApplicationAttribute(@NonNull Attr attribute) {
        if (!"application".equals(attribute.getOwnerElement().getTagName())) {
            return false;
        }
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }
        return "icon".equals(name) || "label".equals(name);
    }

    private static boolean hasOnlyVersionQualifier(@NonNull FolderConfiguration config) {
        ResourceQualifier versionQualifier = config.getVersionQualifier();
        if (!versionQualifier.isValid()) {
            return false;
        }
        for (ResourceQualifier qualifier : config.getQualifiers()) {
            if (qualifier != null && !qualifier.equals(versionQualifier)) {
                return false;
            }
        }
        return true;
    }
}