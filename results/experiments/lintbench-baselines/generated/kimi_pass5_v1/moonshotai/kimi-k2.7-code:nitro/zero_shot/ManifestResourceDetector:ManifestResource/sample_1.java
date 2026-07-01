package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.TAG_APPLICATION;

import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceNamespace;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ManifestResourceDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest resource references",
            "Elements in the manifest can reference resources, but those resources cannot "
                    + "vary across configurations (except as a special case, by version, and except "
                    + "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isTheme() || url.packageName != null || url.type == null) {
            return;
        }

        if (isExempt(attribute)) {
            return;
        }

        ResourceRepository repository = context.getProject().getResourceRepository();
        if (repository == null) {
            return;
        }

        List<ResourceItem> items = repository.getResources(
                ResourceNamespace.RES_AUTO, url.type, url.name);
        if (items.size() <= 1) {
            return;
        }

        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            if (config == null) {
                continue;
            }
            if (hasNonVersionQualifier(config)) {
                String message = "Resources referenced from the manifest cannot vary across "
                        + "configurations except by version";
                context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
                return;
            }
        }
    }

    private static boolean isExempt(Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return false;
        }
        String name = attribute.getLocalName();
        if (!ATTR_LABEL.equals(name) && !ATTR_ICON.equals(name)) {
            return false;
        }
        Element element = attribute.getOwnerElement();
        return element != null && TAG_APPLICATION.equals(element.getTagName());
    }

    private static boolean hasNonVersionQualifier(FolderConfiguration config) {
        if (config.getQualifierCount() == 0) {
            return false;
        }
        return !(config.getQualifierCount() == 1 && config.getVersionQualifier() != null);
    }
}