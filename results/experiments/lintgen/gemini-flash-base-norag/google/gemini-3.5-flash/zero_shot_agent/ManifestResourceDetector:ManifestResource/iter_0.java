package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.SdkConstants;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class ManifestResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    ManifestResourceDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>();
    static {
        ALLOWED_ATTRIBUTES.add("label");
        ALLOWED_ATTRIBUTES.add("icon");
        ALLOWED_ATTRIBUTES.add("roundIcon");
        ALLOWED_ATTRIBUTES.add("theme");
        ALLOWED_ATTRIBUTES.add("logo");
        ALLOWED_ATTRIBUTES.add("banner");
        ALLOWED_ATTRIBUTES.add("description");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (SdkConstants.TAG_META_DATA.equals(tagName)) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node node = attributes.item(i);
            if (node instanceof Attr) {
                Attr attribute = (Attr) node;
                String namespaceUri = attribute.getNamespaceURI();
                if (!SdkConstants.ANDROID_URI.equals(namespaceUri)) {
                    continue;
                }

                String localName = attribute.getLocalName();
                if (ALLOWED_ATTRIBUTES.contains(localName)) {
                    continue;
                }

                String value = attribute.getValue();
                if (value.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
                    ResourceUrl url = ResourceUrl.parse(value);
                    if (url == null || url.isFramework()) {
                        continue;
                    }

                    ResourceRepository repository = context.getClient().getResourceRepository(context.getProject(), true, false);
                    if (repository == null) {
                        continue;
                    }

                    List<ResourceItem> items = repository.getResources(ResourceNamespace.RES_AUTO, url.type, url.name);
                    for (ResourceItem item : items) {
                        FolderConfiguration config = item.getConfiguration();
                        FolderConfiguration cleanConfig = new FolderConfiguration();
                        if (config.getVersionQualifier() != null) {
                            cleanConfig.setVersionQualifier(config.getVersionQualifier());
                        }
                        if (!config.equals(cleanConfig)) {
                            String message = String.format(
                                    "The resource `%s` can vary by configuration (e.g. %s); " +
                                    "this is not supported in the manifest for attribute `%s`",
                                    value,
                                    config.toUniqueString(),
                                    attribute.getName()
                            );
                            context.report(ISSUE, attribute, context.getLocation(attribute), message);
                            break;
                        }
                    }
                }
            }
        }
    }
}