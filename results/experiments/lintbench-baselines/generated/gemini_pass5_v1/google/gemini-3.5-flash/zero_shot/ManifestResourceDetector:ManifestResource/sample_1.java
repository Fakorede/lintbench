package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceNamespace;
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
import java.util.List;
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
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String value = attribute.getValue();
            if (value.startsWith("@") && !value.startsWith("@android:")) {
                String namespace = attribute.getNamespaceURI();
                if (SdkConstants.ANDROID_URI.equals(namespace)) {
                    String name = attribute.getLocalName();
                    if (isExempt(name)) {
                        continue;
                    }
                }

                ResourceUrl url = ResourceUrl.parse(value);
                if (url != null && url.type != null && url.name != null && !url.isFramework()) {
                    checkResource(context, attribute, url);
                }
            }
        }
    }

    private boolean isExempt(String name) {
        return "label".equals(name)
                || "icon".equals(name)
                || "roundIcon".equals(name)
                || "logo".equals(name)
                || "banner".equals(name)
                || "description".equals(name)
                || "theme".equals(name);
    }

    private void checkResource(XmlContext context, Attr attribute, ResourceUrl url) {
        ResourceRepository repository = context.getProject().getResourceRepository();
        if (repository == null) {
            return;
        }

        List<ResourceItem> items = repository.getResources(ResourceNamespace.RES_AUTO, url.type, url.name);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            int count = config.getQualifierCount();
            boolean hasForbiddenQualifier = false;
            for (int i = 0; i < count; i++) {
                ResourceQualifier qualifier = config.getQualifier(i);
                if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                    hasForbiddenQualifier = true;
                    break;
                }
            }

            if (hasForbiddenQualifier) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("Resources referenced from the manifest cannot vary by configurations other than API level; " +
                                  "found variation in %s for %s", config.toUniqueString(), url.toString())
                );
                break;
            }
        }
    }
}