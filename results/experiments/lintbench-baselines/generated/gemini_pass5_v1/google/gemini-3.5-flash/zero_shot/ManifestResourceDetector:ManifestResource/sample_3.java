package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.client.api.ResourceRepositoryScope;
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
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

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

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attrNode = attributes.item(i);
            if (attrNode instanceof Attr) {
                Attr attr = (Attr) attrNode;
                String value = attr.getValue();
                if (value.startsWith("@") && !value.startsWith("@+")) {
                    if (SdkConstants.ANDROID_URI.equals(attr.getNamespaceURI())
                            && isExemptAttribute(attr.getLocalName())) {
                        continue;
                    }
                    checkResource(context, attr, value);
                }
            }
        }
    }

    private void checkResource(@NonNull XmlContext context, @NonNull Attr attr, @NonNull String value) {
        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isFramework()) {
            return;
        }

        ResourceType type = url.type;
        String name = url.name;

        ResourceRepository repository = context.getClient().getResources(context.getProject(), ResourceRepositoryScope.LOCAL);
        if (repository == null) {
            return;
        }

        String namespaceStr = context.getProject().getNamespace();
        ResourceNamespace namespace = namespaceStr != null ? ResourceNamespace.fromPackageName(namespaceStr) : ResourceNamespace.RES_AUTO;

        List<ResourceItem> items = repository.getResources(namespace, type, name);
        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            FolderConfiguration copy = new FolderConfiguration();
            copy.set(config);
            copy.setVersionQualifier(null);
            if (!copy.isDefault()) {
                context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "Manifest resources cannot vary by configuration (except by version qualifier); value `" + value + "` can vary"
                );
                break;
            }
        }
    }

    private static boolean isExemptAttribute(String localName) {
        return "label".equals(localName)
                || "icon".equals(localName)
                || "roundIcon".equals(localName)
                || "logo".equals(localName)
                || "banner".equals(localName)
                || "theme".equals(localName)
                || "description".equals(localName)
                || "sharedUserLabel".equals(localName);
    }
}