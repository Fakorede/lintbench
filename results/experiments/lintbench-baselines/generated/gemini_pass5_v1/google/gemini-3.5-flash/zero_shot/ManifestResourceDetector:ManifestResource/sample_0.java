package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class ManifestResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "ManifestResource",
        "Manifest Resource References",
        "Elements in the manifest can reference resources, but those resources " +
        "cannot vary across configurations (except as a special case, by version, " +
        "and except for a few specific package attributes such as the application " +
        "title and icon).",
        Category.CORRECTNESS,
        6,
        Severity.FATAL,
        new Implementation(
            ManifestResourceDetector.class,
            Scope.MANIFEST_SCOPE
        )
    );

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
        "label", "icon", "roundIcon", "logo", "banner", "description"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String value = attribute.getValue();
            if (value != null && value.startsWith("@")) {
                String namespace = attribute.getNamespaceURI();
                String localName = attribute.getLocalName();
                if (SdkConstants.ANDROID_URI.equals(namespace) && ALLOWED_ATTRIBUTES.contains(localName)) {
                    continue;
                }

                ResourceUrl url = ResourceUrl.parse(value);
                if (url != null && !url.isFramework()) {
                    checkResource(context, attribute, url);
                }
            }
        }
    }

    private void checkResource(@NonNull XmlContext context, @NonNull Attr attribute, @NonNull ResourceUrl url) {
        ResourceRepository repository = context.getClient().getResourceRepository(context.getProject(), true, false);
        if (repository == null) {
            return;
        }

        List<ResourceItem> items = repository.getResources(ResourceNamespace.RES_AUTO, url.type, url.name);
        if (items.isEmpty()) {
            return;
        }

        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            if (hasNonVersionQualifiers(config)) {
                String message = String.format(
                    "The resource `%s` referenced here cannot vary by configurations other than version (found `%s`)",
                    url.toString(),
                    config.getQualifierString()
                );
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    message
                );
                break;
            }
        }
    }

    private boolean hasNonVersionQualifiers(FolderConfiguration config) {
        for (int i = 0; i < FolderConfiguration.getQualifierCount(); i++) {
            ResourceQualifier qualifier = config.getQualifier(i);
            if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                return true;
            }
        }
        return false;
    }
}