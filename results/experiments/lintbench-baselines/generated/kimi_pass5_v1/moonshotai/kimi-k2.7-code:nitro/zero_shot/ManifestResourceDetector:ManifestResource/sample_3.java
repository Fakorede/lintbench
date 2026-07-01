package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.FolderConfiguration;
import com.android.resources.ResourceQualifier;
import com.android.resources.ResourceType;
import com.android.resources.VersionQualifier;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceUrl;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import com.android.tools.lint.res.ResourceFile;
import com.android.tools.lint.res.ResourceItem;
import com.android.tools.lint.res.ResourceRepository;
import java.util.Collection;
import java.util.List;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ManifestResourceDetector.class,
            Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest resource references",
            "Elements in the manifest can reference resources, but those resources cannot "
                    + "vary across configurations (except as a special case, by version, and except "
                    + "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isFramework()) {
            return;
        }

        String element = attribute.getOwnerElement().getTagName();
        String name = attribute.getLocalName();
        if ("application".equals(element)
                && ("label".equals(name) || "icon".equals(name))) {
            return;
        }

        ResourceRepository repository = context.getProject().getResourceRepository();
        if (repository == null) {
            return;
        }

        List<ResourceItem> items = repository.getResourceItem(url.type, url.name);
        if (items == null || items.isEmpty()) {
            return;
        }

        if (hasConfigurationVariation(items)) {
            String message = String.format(
                    "Resources referenced from the manifest cannot vary across configurations "
                            + "(except by version): %1$s",
                    value);
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
        }
    }

    private static boolean hasConfigurationVariation(@NonNull List<ResourceItem> items) {
        for (ResourceItem item : items) {
            ResourceFile source = item.getSource();
            if (source == null) {
                continue;
            }
            FolderConfiguration config = source.getFolderConfiguration();
            if (config == null) {
                continue;
            }
            for (ResourceQualifier qualifier : config.getQualifiers()) {
                if (!(qualifier instanceof VersionQualifier)) {
                    return true;
                }
            }
        }
        return false;
    }
}