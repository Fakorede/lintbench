package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.FolderConfiguration;
import com.android.resources.ResourceQualifier;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.VersionQualifier;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String TOOLS_URI = "http://schemas.android.com/tools";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, "
                            + "and except for a few specific package attributes such as the "
                            + "application title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final Set<String> ALLOWED_CONFIG_DEPENDENT_ATTRIBUTES =
            new HashSet<>(Arrays.asList("icon", "label", "logo", "banner", "roundIcon"));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Detector.XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        if (TOOLS_URI.equals(namespace)) {
            return;
        }

        String value = attribute.getValue();
        if (value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.theme || url.framework) {
            return;
        }

        ResourceType type = url.type;
        if (type == null) {
            return;
        }

        String localName = attribute.getLocalName();
        if (ALLOWED_CONFIG_DEPENDENT_ATTRIBUTES.contains(localName)) {
            return;
        }

        ResourceRepository resources = context.getProject().getResources();
        if (resources == null) {
            return;
        }

        List<ResourceItem> items = resources.getResources(type, url.name);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            if (config == null || config.isDefault()) {
                continue;
            }

            if (isVersionOnly(config)) {
                continue;
            }

            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "The resource `%1$s` referenced from the manifest cannot vary by "
                                    + "configuration; it can only vary by version.",
                            value));
            return;
        }
    }

    private static boolean isVersionOnly(@NonNull FolderConfiguration config) {
        if (config.isDefault()) {
            return true;
        }
        for (ResourceQualifier qualifier : config.getQualifiers()) {
            if (qualifier.isValid() && !(qualifier instanceof VersionQualifier)) {
                return false;
            }
        }
        return true;
    }
}