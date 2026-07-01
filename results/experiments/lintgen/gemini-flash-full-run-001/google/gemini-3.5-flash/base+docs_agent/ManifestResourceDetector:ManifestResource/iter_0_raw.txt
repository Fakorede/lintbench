package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
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

    private static final Set<String> ALLOWED_ATTRIBUTES;
    static {
        Set<String> set = new HashSet<>();
        set.add("label");
        set.add("icon");
        set.add("roundIcon");
        set.add("logo");
        set.add("banner");
        set.add("description");
        set.add("theme");
        ALLOWED_ATTRIBUTES = Collections.unmodifiableSet(set);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        ResourceRepository resources = null;
        boolean resourcesLoaded = false;

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String namespaceUri = attribute.getNamespaceURI();
            if (!SdkConstants.ANDROID_URI.equals(namespaceUri)) {
                continue;
            }

            String localName = attribute.getLocalName();
            if (ALLOWED_ATTRIBUTES.contains(localName)) {
                continue;
            }

            String value = attribute.getValue();
            if (!value.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
                continue;
            }

            ResourceUrl url = ResourceUrl.parse(value);
            if (url == null || url.isFramework()) {
                continue;
            }

            if (!resourcesLoaded) {
                resources = context.getClient().getResourceRepository(context.getProject(), true, false);
                resourcesLoaded = true;
            }

            if (resources == null) {
                continue;
            }

            List<ResourceItem> items = resources.getResources(url.getNamespace(), url.type, url.name);
            if (items.isEmpty()) {
                continue;
            }

            for (ResourceItem item : items) {
                FolderConfiguration config = item.getConfiguration();
                if (hasInvalidQualifiers(config)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            String.format(
                                    "Resources referenced from the manifest cannot vary by configuration " +
                                    "(except by version qualifier), but `%s` also varies by %s",
                                    value,
                                    getInvalidQualifiersString(config)
                            )
                    );
                    break;
                }
            }
        }
    }

    private static boolean hasInvalidQualifiers(FolderConfiguration config) {
        int count = FolderConfiguration.getQualifierCount();
        for (int i = 0; i < count; i++) {
            ResourceQualifier qualifier = config.getQualifier(i);
            if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                return true;
            }
        }
        return false;
    }

    private static String getInvalidQualifiersString(FolderConfiguration config) {
        StringBuilder sb = new StringBuilder();
        int count = FolderConfiguration.getQualifierCount();
        for (int i = 0; i < count; i++) {
            ResourceQualifier qualifier = config.getQualifier(i);
            if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(qualifier.getName());
            }
        }
        return sb.toString();
    }
}