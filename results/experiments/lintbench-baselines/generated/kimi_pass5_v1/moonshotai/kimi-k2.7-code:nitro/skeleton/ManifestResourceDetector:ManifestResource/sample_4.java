package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Resources referenced from the AndroidManifest.xml cannot vary across "
                            + "configurations. This means that you should not reference a resource "
                            + "which has different values depending on the device's configuration "
                            + "(such as locale, orientation, screen density, etc.) unless it is "
                            + "one of the few allowed cases such as the application label and icon. "
                            + "Version-specific resources are also allowed.",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty()
                || !value.startsWith("@")
                || "@null".equals(value)
                || value.startsWith("@android:")
                || value.startsWith("@*android:")
                || value.startsWith("@id/")
                || value.startsWith("@+id/")) {
            return;
        }

        if (isAllowed(attribute)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Resources referenced from the manifest cannot vary across configurations");
    }

    private static boolean isAllowed(Attr attribute) {
        Element element = attribute.getOwnerElement();
        String elementName = element.getLocalName();
        String attributeName = attribute.getLocalName();
        return "application".equals(elementName)
                && ("label".equals(attributeName) || "icon".equals(attributeName));
    }
}