package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources "
                            + "cannot vary across configurations (except as a special case, by "
                            + "version, and except for a few specific package attributes such as "
                            + "the application title and icon).",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@") || value.equals("@null")) {
            return;
        }

        String attributeName = attribute.getLocalName();
        String tagName = attribute.getOwnerElement().getTagName();

        if ("versionName".equals(attributeName) && "manifest".equals(tagName)) {
            return;
        }
        if ("label".equals(attributeName) || "icon".equals(attributeName)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Resources referenced from the manifest cannot vary by configuration");
    }
}