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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ManifestResourceDetector extends Detector implements XmlScanner {

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "label", "icon", "roundIcon", "banner", "logo", "description", "theme"
    ));

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            checkAttribute(context, attr);
        }
    }

    private void checkAttribute(XmlContext context, Attr attr) {
        String value = attr.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        if (value.startsWith("@android:") || value.startsWith("@*android:") || value.equals("@null")) {
            return;
        }

        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            return;
        }

        String ns = attr.getNamespaceURI();
        String localName = attr.getLocalName();
        if (SdkConstants.ANDROID_URI.equals(ns) && ALLOWED_ATTRIBUTES.contains(localName)) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isFramework() || url.type == null || url.name == null) {
            return;
        }

        ResourceRepository repository = context.getProject().getResourceRepository();
        if (repository == null) {
            return;
        }

        List<ResourceItem> items = repository.getResources(null, url.type, url.name);
        if (items.isEmpty()) {
            return;
        }

        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            if (config == null) {
                continue;
            }
            ResourceQualifier[] qualifiers = config.getQualifiers();
            if (qualifiers == null) {
                continue;
            }
            for (ResourceQualifier qualifier : qualifiers) {
                if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                    String message = String.format(
                            "Resource reference `%s` in the manifest should not vary by configuration " +
                            "(found in folder `%s`). Only version qualifiers are allowed.",
                            value, config.getFolderName());
                    context.report(ISSUE, attr, context.getLocation(attr), message);
                    return;
                }
            }
        }
    }
}