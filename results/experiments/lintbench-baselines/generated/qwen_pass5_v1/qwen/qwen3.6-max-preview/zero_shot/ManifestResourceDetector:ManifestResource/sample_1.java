package com.android.tools.lint.checks;

import com.android.resources.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceItem;
import com.android.tools.lint.detector.api.ResourceRepository;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;

import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

public class ManifestResourceDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestResourceDetector.class, EnumSet.of(Scope.MANIFEST))
    );

    private static final Pattern VERSION_ONLY = Pattern.compile("^(-v\\d+)*$");
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        String localName = attribute.getLocalName();
        if (isAllowedAttribute(localName)) {
            return;
        }

        String ref = value.substring(1);
        if (ref.startsWith("+")) {
            ref = ref.substring(1);
        }
        if ("null".equals(ref)) {
            return;
        }

        boolean isFramework = ref.startsWith("android:");
        if (isFramework) {
            ref = ref.substring(8);
        }

        int slash = ref.indexOf('/');
        if (slash == -1) {
            return;
        }

        String typeStr = ref.substring(0, slash);
        String name = ref.substring(slash + 1);
        ResourceType type = ResourceType.getEnum(typeStr);
        if (type == null) {
            return;
        }

        ResourceRepository repository = context.getResourceRepository();
        if (repository == null) {
            return;
        }

        ResourceNamespace ns = isFramework ? ResourceNamespace.ANDROID : ResourceNamespace.TODO;
        List<ResourceItem> items = repository.getResources(ns, type, name);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (ResourceItem item : items) {
            String qualifiers = item.getQualifiers();
            if (qualifiers != null && !qualifiers.isEmpty()) {
                if (!VERSION_ONLY.matcher(qualifiers).matches()) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "Resources referenced in the manifest cannot vary by configuration " +
                            "(except for version qualifiers). Found configuration qualifiers: " + qualifiers);
                    return;
                }
            }
        }
    }

    private static boolean isAllowedAttribute(String localName) {
        return "label".equals(localName) ||
               "icon".equals(localName) ||
               "roundIcon".equals(localName) ||
               "banner".equals(localName) ||
               "logo".equals(localName) ||
               "description".equals(localName);
    }
}