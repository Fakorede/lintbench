package com.android.tools.lint.checks;

import com.android.resources.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceItem;
import com.android.resources.ResourceNamespace;
import com.android.resources.ResourceQualifier;
import com.android.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.VersionQualifier;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlDetector;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ManifestResourceDetector extends XmlDetector {
    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "icon", "roundIcon", "banner", "logo", "label", "description", "theme"
    ));

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            if (value != null && value.startsWith("@")) {
                ResourceUrl url = ResourceUrl.parse(value);
                if (url != null && url.isValid() && !url.isFramework()) {
                    String localName = attr.getLocalName();
                    if (localName != null && ALLOWED_ATTRIBUTES.contains(localName)) {
                        continue;
                    }
                    if (variesByConfiguration(context, url)) {
                        context.report(ISSUE, attr, context.getLocation(attr),
                                "Resource references in the manifest cannot vary by configuration " +
                                "(except for version qualifiers and specific attributes like icon/label)");
                    }
                }
            }
        }
    }

    private static boolean variesByConfiguration(@NotNull XmlContext context, @NotNull ResourceUrl url) {
        try {
            ResourceRepository repo = context.getClient().getResourceRepository(context.getProject(), false);
            if (repo == null) {
                return false;
            }
            List<ResourceItem> items = repo.getResources(ResourceNamespace.RES_AUTO, url.type, url.name);
            if (items == null || items.isEmpty()) {
                return false;
            }
            for (ResourceItem item : items) {
                String folderName = item.getConfiguration().getFolderName();
                FolderConfiguration config = FolderConfiguration.getConfigForFolder(folderName);
                if (config != null) {
                    for (ResourceQualifier q : config.getQualifiers()) {
                        if (q != null && !(q instanceof VersionQualifier)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Gracefully handle API variations or missing resources
        }
        return false;
    }
}