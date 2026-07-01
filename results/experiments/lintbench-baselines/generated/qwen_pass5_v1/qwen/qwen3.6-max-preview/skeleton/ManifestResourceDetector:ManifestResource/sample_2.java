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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "label", "icon", "roundIcon", "banner", "logo", "description"
    ));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!context.isManifest()) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || (!value.startsWith("@") && !value.startsWith("?"))) {
            return;
        }

        String ns = attribute.getNamespaceURI();
        if (!ANDROID_NS.equals(ns)) {
            return;
        }

        String name = attribute.getLocalName();
        if (ALLOWED_ATTRIBUTES.contains(name)) {
            return;
        }

        context.report(ISSUE, attribute, context.getLocation(attribute),
                "Resources referenced in the manifest cannot vary by configuration " +
                "(except for version qualifiers, and except for specific attributes like label and icon).");
    }
}