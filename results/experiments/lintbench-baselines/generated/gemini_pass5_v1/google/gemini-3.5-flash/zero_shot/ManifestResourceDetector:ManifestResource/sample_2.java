package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceNamespace;
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
            Node attrNode = attributes.item(i);
            if (attrNode instanceof Attr) {
                Attr attr = (Attr) attrNode;
                String value = attr.getValue();
                if (value == null) {
                    continue;
                }

                ResourceUrl url = ResourceUrl.parse(value);
                if (url != null && !url.isFramework() && !url.isCreate()) {
                    String localName = attr.getLocalName();
                    if (isAllowedAttribute(localName)) {
                        continue;
                    }

                    if (variesByConfiguration(context, url)) {
                        context.report(
                                ISSUE,
                                attr,
                                context.getLocation(attr),
                                "Resource `" + value + "` cannot vary by configuration when referenced from `android:" + localName + "`"
                        );
                    }
                }
            }
        }
    }

    private boolean isAllowedAttribute(@Nullable String name) {
        if (name == null) {
            return false;
        }
        switch (name) {
            case "label":
            case "icon":
            case "roundIcon":
            case "logo":
            case "banner":
            case "theme":
            case "description":
            case "sharedUserLabel":
                return true;
            default:
                return false;
        }
    }

    private boolean variesByConfiguration(@NonNull XmlContext context, @NonNull ResourceUrl url) {
        ResourceRepository repository = context.getClient().getResourceRepository(context.getProject(), true, false);
        if (repository == null) {
            return false;
        }

        ResourceType type = url.type;
        String name = url.name;
        if (type == null || name == null) {
            return false;
        }

        Collection<ResourceItem> items = repository.getResources(ResourceNamespace.RES_AUTO, type, name);
        if (items.isEmpty()) {
            items = repository.getResources(ResourceNamespace.TODO, type, name);
        }

        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            if (config != null) {
                for (ResourceQualifier qualifier : config.getQualifiers()) {
                    if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}