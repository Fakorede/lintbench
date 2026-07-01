package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import org.w3c.dom.Attr;
import java.util.Collection;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, and "
                            + "except for a few specific package attributes such as the application "
                            + "title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    new Implementation(
                            ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ATTR_LABEL = "label";
    private static final String ATTR_ICON = "icon";
    private static final String ATTR_ROUND_ICON = "roundIcon";
    private static final String ATTR_LOGO = "logo";
    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_DESCRIPTION = "description";

    private static boolean isAllowedToVary(String name) {
        return ATTR_LABEL.equals(name)
                || ATTR_ICON.equals(name)
                || ATTR_ROUND_ICON.equals(name)
                || ATTR_LOGO.equals(name)
                || ATTR_BANNER.equals(name)
                || ATTR_DESCRIPTION.equals(name);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }
        if (value.startsWith("@android:") || value.startsWith("@sys:")) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        if (namespace != null && !namespace.equals("http://schemas.android.com/apk/res/android")) {
            return;
        }

        String localName = attribute.getLocalName();
        if (isAllowedToVary(localName)) {
            return;
        }

        int slash = value.indexOf('/');
        if (slash == -1) {
            return;
        }

        int colon = value.indexOf(':');
        String type;
        String name;
        if (colon != -1 && colon < slash) {
            type = value.substring(colon + 1, slash);
        } else {
            type = value.substring(1, slash);
        }
        name = value.substring(slash + 1);

        if ("id".equals(type) || "+id".equals(type)) {
            return;
        }

        com.android.tools.lint.client.api.LintClient client = context.getClient();
        com.android.tools.lint.detector.api.Project project = context.getProject();
        com.android.ide.common.resources.ResourceRepository repository = client.getResourceRepository(project, true, false);
        if (repository == null) {
            return;
        }

        com.android.resources.ResourceType resourceType = com.android.resources.ResourceType.fromXmlValue(type);
        if (resourceType == null) {
            return;
        }

        java.util.List<com.android.ide.common.resources.ResourceItem> items = 
                repository.getResources(com.android.resources.ResourceNamespace.RES_AUTO, resourceType, name);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (com.android.ide.common.resources.ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config = item.getConfiguration();
            if (config != null) {
                String qualifierString = config.getQualifierString();
                if (!qualifierString.isEmpty() && !qualifierString.matches("v\\d+")) {
                    String message = String.format(
                            "Resource `%s` cannot vary by configuration (at `%s`) except by version",
                            value,
                            qualifierString);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                    break;
                }
            }
        }
    }
}