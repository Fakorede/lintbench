package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

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
                    new Implementation(
                            ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "icon", "label", "roundIcon", "banner", "logo", "description", "theme"
    ));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.startsWith("@")) {
            String name = attribute.getLocalName();
            if (name != null && !ALLOWED_ATTRIBUTES.contains(name)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Resource references in the manifest cannot vary by configuration "
                                + "(except for version, and except for attributes like icon and label)");
            }
        }
    }
}