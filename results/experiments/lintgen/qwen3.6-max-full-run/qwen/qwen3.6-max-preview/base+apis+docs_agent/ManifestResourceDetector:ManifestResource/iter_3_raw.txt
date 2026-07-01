package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.ResourceReference;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ManifestResourceDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "label", "icon", "roundIcon", "banner", "logo", "description", "theme", "resource", "value"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!element.hasAttributes()) {
            return;
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            checkAttribute(context, attr);
        }
    }

    private void checkAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (!value.startsWith("@")) {
            return;
        }

        if (value.startsWith("@android:") || value.startsWith("@+id/") || value.startsWith("@id/") || value.equals("@null")) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getNodeName();
        }
        int colonIndex = name.indexOf(':');
        if (colonIndex != -1) {
            name = name.substring(colonIndex + 1);
        }

        if (ALLOWED_ATTRIBUTES.contains(name)) {
            return;
        }

        context.report(ISSUE, attribute, context.getValueLocation(attribute),
                "Resource references in the manifest are not allowed for this attribute. " +
                "Manifest resources cannot vary across configurations except for version qualifiers, " +
                "and are only permitted for specific attributes like icon and label.");
    }
}