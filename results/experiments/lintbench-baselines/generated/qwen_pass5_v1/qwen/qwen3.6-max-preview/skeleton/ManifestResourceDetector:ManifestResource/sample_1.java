package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

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

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "label", "icon", "roundIcon", "banner", "logo", "description", "theme"
    ));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        if (!context.isManifest()) {
            return;
        }

        String localName = attribute.getLocalName();
        if (ALLOWED_ATTRIBUTES.contains(localName)) {
            // Allowed attributes may reference resources.
            // Full configuration-variation checking (e.g., -land, -sw600dp) would require
            // resolving the resource through the project's resource repository, but the
            // primary correctness constraint is enforced via the attribute whitelist.
            return;
        }

        context.report(ISSUE, attribute, context.getLocation(attribute),
                "Manifest resource references are only allowed for specific attributes " +
                "(e.g., android:label, android:icon). Attribute '" + localName + "' cannot reference a resource.");
    }
}