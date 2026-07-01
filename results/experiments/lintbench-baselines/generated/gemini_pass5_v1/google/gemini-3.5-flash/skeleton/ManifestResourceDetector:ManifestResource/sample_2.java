package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, and except "
                            + "for a few specific package attributes such as the application title and icon).",
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
        if (isIgnoredAttribute(attribute)) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@") || value.startsWith("@android:") || value.startsWith("@+")) {
            return;
        }

        int slash = value.indexOf('/');
        if (slash == -1) {
            return;
        }

        String type = value.substring(1, slash);
        String name = value.substring(slash + 1);

        if (type.contains(":")) {
            return;
        }

        if (hasNonVersionQualifiers(context, type, name)) {
            String message = String.format(
                    "Resources referenced from the manifest cannot vary by configuration (except by version), but `%s` does",
                    value);
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
        }
    }

    private boolean isIgnoredAttribute(@NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        if (name.startsWith("android:")) {
            name = name.substring("android:".length());
        }

        if (name.equals("label")
                || name.equals("icon")
                || name.equals("roundIcon")
                || name.equals("banner")
                || name.equals("logo")
                || name.equals("description")
                || name.equals("theme")) {
            return true;
        }

        Element parent = attribute.getOwnerElement();
        if (parent != null) {
            String parentName = parent.getLocalName();
            if (parentName == null) {
                parentName = parent.getTagName();
            }
            if (parentName.equals("meta-data") && (name.equals("value") || name.equals("resource"))) {
                return true;
            }
        }

        return false;
    }

    private boolean hasNonVersionQualifiers(XmlContext context, String type, String name) {
        try {
            Object repository = context.getClient().getResourceRepository(context.getProject(), true, false);
            if (repository == null) {
                return false;
            }

            Class<?> namespaceClass = Class.forName("com.android.resources.ResourceNamespace");
            Object resAuto = namespaceClass.getField("RES_AUTO").get(null);

            Class<?> resourceTypeClass = Class.forName("com.android.resources.ResourceType");
            Object resourceType = null;
            try {
                resourceType = resourceTypeClass.getMethod("fromClassName", String.class).invoke(null, type);
            } catch (Throwable t) {
                try {
                    resourceType = resourceTypeClass.getMethod("fromValue", String.class).invoke(null, type);
                } catch (Throwable t2) {
                    resourceType = resourceTypeClass.getMethod("valueOf", String.class).invoke(null, type.toUpperCase(java.util.Locale.US));
                }
            }

            if (resourceType == null) {
                return false;
            }

            Method getResourcesMethod = repository.getClass().getMethod("getResources", namespaceClass, resourceTypeClass, String.class);
            List<?> items = (List<?>) getResourcesMethod.invoke(repository, resAuto, resourceType, name);
            if (items == null || items.isEmpty()) {
                return false;
            }

            for (Object item : items) {
                Method getConfigurationMethod = item.getClass().getMethod("getConfiguration");
                Object folderConfig = getConfigurationMethod.invoke(item);
                if (folderConfig != null) {
                    String configStr = folderConfig.toString();
                    if (!isAllowedConfig(configStr)) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            // Fallback gracefully on any reflection or loading issues
        }
        return false;
    }

    private boolean isAllowedConfig(String configStr) {
        if (configStr == null || configStr.isEmpty()) {
            return true;
        }
        String[] parts = configStr.split("-");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!part.matches("v\\d+")) {
                return false;
            }
        }
        return true;
    }
}