package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
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
            Severity.ERROR,
            new Implementation(ManifestResourceDetector.class, EnumSet.of(Scope.MANIFEST)));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "label",
            "icon",
            "roundIcon",
            "banner",
            "description",
            "logo",
            "theme"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attr = (Attr) element.getAttributes().item(i);
            String value = attr.getValue();
            if (value != null && value.startsWith("@")
                    && !value.startsWith("@android:")
                    && !value.startsWith("@*android:")
                    && !value.equals("@null")) {

                if (!ANDROID_URI.equals(attr.getNamespaceURI())) {
                    continue;
                }

                String localName = attr.getLocalName();
                if (localName == null) {
                    localName = attr.getName();
                }

                if (!ALLOWED_ATTRIBUTES.contains(localName)) {
                    context.report(ISSUE, context.getLocation(attr),
                            "Resources referenced in the manifest cannot vary by configuration " +
                            "(except for version qualifiers, e.g. `-v21`), but this reference " +
                            "is in `" + attr.getName() + "` which can vary by configuration.");
                }
            }
        }
    }
}