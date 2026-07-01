package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.ResourceReference;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.uast.UElement;
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
            new Implementation(ManifestResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (!value.startsWith("@") && !value.startsWith("?")) {
            return;
        }

        String localName = attribute.getLocalName();
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