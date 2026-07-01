package com.android.tools.lint.checks;

import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.List;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot "
                    + "vary across configurations (except as a special case, by version, and "
                    + "except for a few specific package attributes such as the application "
                    + "title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isCreate() || url.isTheme()) {
            return;
        }

        ResourceType type = url.getResourceType();
        if (type == null) {
            return;
        }

        String name = url.getName();

        Project project = context.getProject();
        ResourceRepository resources = project.getResourceRepository();
        if (resources == null) {
            return;
        }

        List<ResourceItem> items = resources.getResourceItem(type, name);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            if (!isVersionOnly(config)) {
                String attrName = attribute.getLocalName();
                Element owner = attribute.getOwnerElement();
                String elementName = owner.getLocalName();
                if ("application".equals(elementName)
                        && ("icon".equals(attrName) || "label".equals(attrName))) {
                    return;
                }

                String message = String.format(
                        "The resource `@%1$s/%2$s` has variants across configurations, "
                                + "but resources referenced from the manifest cannot vary "
                                + "by configuration (except by version)",
                        type.getName(), name);
                context.report(ISSUE, context.getValueLocation(attribute), message);
                return;
            }
        }
    }

    private static boolean isVersionOnly(FolderConfiguration config) {
        if (config == null) {
            return true;
        }

        for (ResourceQualifier qualifier : config.getQualifiers()) {
            if (qualifier != null
                    && qualifier.isValid()
                    && !(qualifier instanceof VersionQualifier)) {
                return false;
            }
        }

        return true;
    }
}