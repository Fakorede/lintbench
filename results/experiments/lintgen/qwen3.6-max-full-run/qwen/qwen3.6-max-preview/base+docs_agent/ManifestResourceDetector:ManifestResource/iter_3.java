package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "icon", "roundIcon", "logo", "banner", "label", "description", "theme", "resource", "value"
    ));

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

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attr) {
        String value = attr.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        if (value.startsWith("@android:") || value.startsWith("@*android:") || value.equals("@null")) {
            return;
        }

        String ns = attr.getNamespaceURI();
        if (!SdkConstants.ANDROID_URI.equals(ns)) {
            return;
        }

        String localName = attr.getLocalName();
        if (localName == null) {
            localName = attr.getName();
            if (localName.contains(":")) {
                localName = localName.substring(localName.indexOf(':') + 1);
            }
        }

        if (!ALLOWED_ATTRIBUTES.contains(localName)) {
            String message = String.format(
                    "Resources referenced in the manifest cannot vary by configuration " +
                    "(except for version qualifiers, e.g. -v21). Found reference in `%s`.",
                    localName);
            context.report(ISSUE, context.getLocation(attr), message);
        }
    }
}