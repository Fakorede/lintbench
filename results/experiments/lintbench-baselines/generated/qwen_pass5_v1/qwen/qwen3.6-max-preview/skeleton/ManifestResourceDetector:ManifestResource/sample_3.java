package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.client.api.ResourceRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot " +
                    "vary across configurations (except as a special case, by version, and except " +
                    "for a few specific package attributes such as the application title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!context.isManifestFile()) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        String ns = attribute.getNamespaceURI();
        String localName = attribute.getLocalName();
        boolean isAllowed = "http://schemas.android.com/apk/res/android".equals(ns) && (
                "label".equals(localName) || "icon".equals(localName) || "roundIcon".equals(localName) ||
                "logo".equals(localName) || "banner".equals(localName) || "description".equals(localName)
        );
        if (isAllowed) {
            return;
        }

        String ref = value.substring(1);
        if (ref.startsWith("+")) {
            ref = ref.substring(1);
        }
        boolean isFramework = ref.startsWith("android:");
        if (isFramework) {
            ref = ref.substring("android:".length());
        }

        int slash = ref.indexOf('/');
        if (slash == -1) {
            return;
        }

        String typeStr = ref.substring(0, slash);
        String name = ref.substring(slash + 1);
        com.android.resources.ResourceType type = com.android.resources.ResourceType.getEnum(typeStr);
        if (type == null) {
            return;
        }

        ResourceRepository repository = context.getClient().getResourceRepository(context.getProject(), isFramework);
        if (repository == null) {
            return;
        }

        com.android.resources.ResourceNamespace namespace = isFramework
                ? com.android.resources.ResourceNamespace.ANDROID
                : com.android.resources.ResourceNamespace.RES_AUTO;

        List<com.android.resources.ResourceItem> items = repository.getResources(namespace, type, name);
        if (items.isEmpty()) {
            return;
        }

        for (com.android.resources.ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config = item.getConfiguration();
            if (config != null && hasNonVersionQualifier(config)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Manifest references to resources should not vary by configuration (except for version qualifiers). " +
                        "This resource varies by configuration, which can cause runtime errors.");
                break;
            }
        }
    }

    private static boolean hasNonVersionQualifier(com.android.ide.common.resources.configuration.FolderConfiguration config) {
        String folderName = config.getFolderName();
        if (folderName == null) {
            return false;
        }
        String[] parts = folderName.split("-");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (!part.startsWith("v") || !part.substring(1).matches("\\d+")) {
                return true;
            }
        }
        return false;
    }
}