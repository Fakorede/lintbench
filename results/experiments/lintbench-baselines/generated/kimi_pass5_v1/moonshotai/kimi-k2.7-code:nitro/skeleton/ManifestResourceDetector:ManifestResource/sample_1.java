package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.tools.lint.client.api.ResourceRepository;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceUrl;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import java.util.List;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final String TAG_APPLICATION = "application";
    private static final String ATTR_LABEL = "label";
    private static final String ATTR_ICON = "icon";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Resources referenced from the AndroidManifest.xml cannot vary based on device "
                            + "configuration. For example, you cannot reference a string that is "
                            + "translated into different languages, or a drawable that has "
                            + "configuration-specific variants. The manifest is built once and its "
                            + "resource references are resolved independently of runtime "
                            + "configuration. The only allowed configuration qualifier is a "
                            + "version qualifier (e.g. values-v14), and a small number of "
                            + "<application> attributes such as android:label and android:icon "
                            + "are exempt.",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isFramework()) {
            return;
        }

        if (isExempt(attribute)) {
            return;
        }

        ResourceRepository resources =
                context.getDriver().getClient().getResources(context.getProject(), true);
        if (resources == null) {
            return;
        }

        List<ResourceItem> items = resources.getResources(url.type, url.name);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (ResourceItem item : items) {
            FolderConfiguration config = item.getConfiguration();
            if (config != null && !isVersionOnly(config)) {
                String message =
                        String.format(
                                "Resources referenced from the manifest cannot vary based on "
                                        + "configuration (except for version qualifiers). The "
                                        + "resource `%1$s` has a variant for `%2$s`.",
                                value, config.getFolderName());
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
                return;
            }
        }
    }

    private static boolean isExempt(@NonNull Attr attribute) {
        String tag = attribute.getOwnerElement().getTagName();
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        return TAG_APPLICATION.equals(tag)
                && (ATTR_LABEL.equals(name) || ATTR_ICON.equals(name));
    }

    private static boolean isVersionOnly(@NonNull FolderConfiguration config) {
        for (ResourceQualifier qualifier : config.getQualifiers()) {
            if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                return false;
            }
        }
        return true;
    }
}