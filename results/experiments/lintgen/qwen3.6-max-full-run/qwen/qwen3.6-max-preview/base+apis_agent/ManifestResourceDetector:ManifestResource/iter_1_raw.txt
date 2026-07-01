package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ManifestResourceDetector extends Detector implements XmlScanner {

    private static final Set<String> ALLOWED_VARIABLE_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "label", "icon", "roundIcon", "banner", "logo", "description"
    ));

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!context.file.getName().equals("AndroidManifest.xml")) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (!value.startsWith("@") && !value.startsWith("?")) {
            return;
        }

        String localName = attribute.getLocalName();
        if (localName == null) {
            localName = attribute.getName();
        }

        if (ALLOWED_VARIABLE_ATTRIBUTES.contains(localName)) {
            return;
        }

        if (value.startsWith("@android:") || value.startsWith("?android:")) {
            return;
        }

        context.report(ISSUE, attribute, context.getLocation(attribute),
                "Manifest resource reference `" + value + "` on attribute `" + localName +
                "` may vary across configurations. Manifest resources should only vary by version, " +
                "except for specific attributes like label, icon, banner, etc.");
    }
}