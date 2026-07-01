package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ManifestResourceDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "icon",
            "roundIcon",
            "banner",
            "logo",
            "label",
            "description",
            "theme"
    ));

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        boolean isResourceRef = value.startsWith("@") || value.startsWith("?");
        if (!isResourceRef) {
            return;
        }

        // Framework resources are static and do not vary by app configuration
        if (value.startsWith("@android:") || value.startsWith("?android:")) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        if (!ALLOWED_ATTRIBUTES.contains(name)) {
            context.report(
                    ISSUE,
                    context.getLocation(attribute),
                    "Resource references are not allowed for this manifest attribute. " +
                    "Only specific attributes (like icon, label, theme, etc.) support " +
                    "configuration-varying resources.");
        }
    }
}