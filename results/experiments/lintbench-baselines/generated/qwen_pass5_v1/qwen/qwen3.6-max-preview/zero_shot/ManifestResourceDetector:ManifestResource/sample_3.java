package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlDetector;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ManifestResourceDetector extends XmlDetector {
    public static final Issue ISSUE = Issue.create(
        "ManifestResource",
        "Manifest Resource References",
        "Elements in the manifest can reference resources, but those resources cannot " +
        "vary across configurations (except as a special case, by version, and except " +
        "for a few specific package attributes such as the application title and icon).",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> ALLOWED_ELEMENTS = new HashSet<>(Arrays.asList(
        "application", "activity", "activity-alias", "service", "receiver", "provider"
    ));

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
        "icon", "roundIcon", "label", "banner", "logo", "description"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }
        if (value.startsWith("@android:") || value.equals("@null")) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        String elementName = owner.getLocalName();
        String attributeName = attribute.getLocalName();

        if (ALLOWED_ELEMENTS.contains(elementName) && ALLOWED_ATTRIBUTES.contains(attributeName)) {
            return;
        }

        context.report(ISSUE, attribute, context.getLocation(attribute),
            "Resources referenced in the manifest should not vary across configurations " +
            "(except by version, or for specific attributes like icon and label).");
    }
}