package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.sdklib.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ManifestResourceDetector extends ResourceXmlDetector {

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
                            ManifestResourceDetector.class,
                            Scope.MANIFEST_SCOPE));

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "label", "icon", "roundIcon", "banner", "logo", "description"
    ));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        String localName = attribute.getLocalName();
        if (ALLOWED_ATTRIBUTES.contains(localName)) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || !url.isValid() || url.type == null || url.framework) {
            return;
        }

        try {
            List<?> items = context.getResourceResolver().getResources(url.type, url.name);
            if (items.isEmpty()) {
                return;
            }

            Object item = items.get(0);
            File file = null;
            try {
                Object source = item.getClass().getMethod("getSource").invoke(item);
                file = (File) source.getClass().getMethod("getFile").invoke(source);
            } catch (Exception ignored) {
                try {
                    file = (File) item.getClass().getMethod("getSourceFile").invoke(item);
                } catch (Exception ignored2) {
                    return;
                }
            }

            if (file == null || file.getParentFile() == null) {
                return;
            }

            String folderName = file.getParentFile().getName();
            String[] parts = folderName.split("-");
            for (int i = 1; i < parts.length; i++) {
                String qualifier = parts[i];
                if (qualifier.startsWith("v") && qualifier.length() > 1) {
                    String num = qualifier.substring(1);
                    if (num.matches("\\d+")) {
                        continue;
                    }
                }
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Resources referenced in the manifest cannot vary by configuration "
                                + "(except for version qualifiers and a few specific attributes). "
                                + "Invalid configuration qualifier: " + qualifier);
                return;
            }
        } catch (Exception ignored) {
            // Gracefully handle API differences or resolution failures
        }
    }
}