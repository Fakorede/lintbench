package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

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

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("meta-data".equals(element.getTagName())) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String value = attribute.getValue();
            if (value == null || !value.startsWith("@")) {
                continue;
            }

            if (value.startsWith("@id/") || value.startsWith("@+id/")) {
                continue;
            }

            if (value.startsWith("@android:")) {
                continue;
            }

            String namespace = attribute.getNamespaceURI();
            if (!SdkConstants.ANDROID_URI.equals(namespace)) {
                continue;
            }

            String localName = attribute.getLocalName();
            if (isAllowedToVary(localName)) {
                continue;
            }

            ResourceUrl url = ResourceUrl.parse(value);
            if (url == null || url.isFramework() || url.type == ResourceType.ID) {
                continue;
            }

            ResourceRepository repository = context.getClient().getResourceRepository(context.getProject(), true, false);
            if (repository == null) {
                continue;
            }

            Collection<ResourceItem> items = repository.getResources(ResourceNamespace.RES_AUTO, url.type, url.name);
            for (ResourceItem item : items) {
                FolderConfiguration config = item.getConfiguration();
                String qualifier = getFirstNonVersionQualifier(config);
                if (qualifier != null) {
                    String message = String.format(
                            "The resource `%s` value referenced here can vary by configuration " +
                            "(such as qualifier `%s`)",
                            value, qualifier);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                    break;
                }
            }
        }
    }

    private boolean isAllowedToVary(String localName) {
        return "label".equals(localName)
                || "icon".equals(localName)
                || "roundIcon".equals(localName)
                || "logo".equals(localName)
                || "banner".equals(localName)
                || "description".equals(localName)
                || "theme".equals(localName)
                || "sharedUserLabel".equals(localName);
    }

    private String getFirstNonVersionQualifier(FolderConfiguration config) {
        if (config == null) {
            return null;
        }
        int count = FolderConfiguration.getQualifierCount();
        for (int i = 0; i < count; i++) {
            ResourceQualifier qualifier = config.getQualifier(i);
            if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                return qualifier.getFolderSegment();
            }
        }
        return null;
    }
}