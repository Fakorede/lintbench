package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceUrl;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Resources referenced from the AndroidManifest.xml file cannot vary across "
                            + "configurations. The manifest is processed as a single file, so the "
                            + "resource value is chosen at build time using the default "
                            + "configuration. References to resources that have "
                            + "configuration-specific alternatives (for example, different values "
                            + "for different languages, screen densities, orientations, etc.) are "
                            + "therefore not allowed. The only exceptions are resources that vary "
                            + "by API version (e.g. values-v21), and a small number of package "
                            + "attributes such as the application title (android:label) and icon "
                            + "(android:icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isTheme() || url.type == null || url.isFramework()) {
            return;
        }

        // ID references (such as @+id/...) are not configuration-varying resources.
        if ("id".equals(url.type.getName())) {
            return;
        }

        if (isAllowedAttribute(attribute.getLocalName())) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                String.format(
                        "The resource `%1$s` is referenced from the manifest, but manifest "
                                + "resources cannot vary by configuration (except by version).",
                        value));
    }

    private static boolean isAllowedAttribute(String name) {
        // The application title and icon (and related visual attributes) are allowed to reference
        // configuration-dependent resources.
        return "label".equals(name)
                || "icon".equals(name)
                || "roundIcon".equals(name)
                || "banner".equals(name)
                || "logo".equals(name);
    }
}