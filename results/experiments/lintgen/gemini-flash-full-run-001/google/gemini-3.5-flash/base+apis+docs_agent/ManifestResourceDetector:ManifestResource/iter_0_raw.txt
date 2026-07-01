package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceNamespace;
import com.android.resources.ResourceType;
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
        Severity.WARNING,
        new Implementation(
            ManifestResourceDetector.class,
            Scope.MANIFEST_SCOPE
        )
    );

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return null; // Visit all elements
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getTagName().equals("meta-data")) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attrNode = attributes.item(i);
            if (attrNode instanceof Attr) {
                Attr attr = (Attr) attrNode;
                String value = attr.getValue();
                if (value.startsWith("@") && !value.startsWith("@android:") && !value.startsWith("@null")) {
                    String localName = attr.getLocalName();
                    if (isAllowedToVary(localName)) {
                        continue;
                    }

                    String url = value;
                    int colon = url.indexOf(':');
                    int slash = url.indexOf('/');
                    if (slash != -1) {
                        String type = url.substring(url.startsWith("@+") ? 2 : 1, slash);
                        if (colon != -1) {
                            type = url.substring(colon + 1, slash);
                        }
                        String name = url.substring(slash + 1);

                        if (type.equals("id")) {
                            continue;
                        }

                        ResourceType resourceType = ResourceType.fromXmlValue(type);
                        if (resourceType != null) {
                            ResourceRepository repository = context.getClient().getResourceRepository(context.getProject(), true, false);
                            if (repository != null) {
                                List<ResourceItem> items = repository.getResources(ResourceNamespace.RES_AUTO, resourceType, name);
                                boolean varies = false;
                                String varyingFolder = null;
                                for (ResourceItem item : items) {
                                    String path = item.getSource() != null ? item.getSource().toString() : null;
                                    if (path != null) {
                                        path = path.replace('\\', '/');
                                        int lastSlash = path.lastIndexOf('/');
                                        if (lastSlash != -1) {
                                            int prevSlash = path.lastIndexOf('/', lastSlash - 1);
                                            if (prevSlash != -1) {
                                                String folderName = path.substring(prevSlash + 1, lastSlash);
                                                String[] parts = folderName.split("-");
                                                for (int j = 1; j < parts.length; j++) {
                                                    String qualifier = parts[j];
                                                    if (!qualifier.matches("v\\d+")) {
                                                        varies = true;
                                                        varyingFolder = folderName;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (varies) {
                                        break;
                                    }
                                }

                                if (varies) {
                                    String message = String.format(
                                        "Resource `%s` can vary by configuration (defined in `%s`)",
                                        value, varyingFolder
                                    );
                                    context.report(ISSUE, attr, context.getValueLocation(attr), message);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isAllowedToVary(String localName) {
        if (localName == null) {
            return false;
        }
        switch (localName) {
            case "label":
            case "icon":
            case "roundIcon":
            case "logo":
            case "banner":
            case "description":
            case "theme":
                return true;
            default:
                return false;
        }
    }
}