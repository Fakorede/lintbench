package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.FolderConfiguration;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ManifestResourceDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String TAG_APPLICATION = "application";
    private static final String ATTR_LABEL = "label";
    private static final String ATTR_ICON = "icon";

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Resources in manifest cannot vary across configurations",
            "Elements in the manifest can reference resources, but those resources cannot "
                    + "vary across configurations (except as a special case, by version, and "
                    + "except for a few specific package attributes such as the application "
                    + "title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        String value = attribute.getValue();
        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.theme || url.framework) {
            return;
        }

        if (isApplicationLabelOrIcon(attribute)) {
            return;
        }

        ResourceRepository repository = context.getProject().getResourceRepository();
        if (repository == null) {
            return;
        }

        ResourceItem item = repository.getResource(url.type, url.name);
        if (item == null) {
            return;
        }

        if (isConfigurationDependent(item)) {
            String message = String.format(
                    "The resource `%1$s` varies across configurations; it cannot be used in the manifest",
                    value);
            Location location = context.getValueLocation(attribute);
            context.report(ISSUE, attribute, location, message);
        }
    }

    private static boolean isApplicationLabelOrIcon(@NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (!ATTR_LABEL.equals(name) && !ATTR_ICON.equals(name)) {
            return false;
        }
        Element owner = attribute.getOwnerElement();
        return owner != null && TAG_APPLICATION.equals(owner.getTagName());
    }

    private static boolean isConfigurationDependent(@NonNull ResourceItem item) {
        if (hasNonVersionQualifiers(item)) {
            return true;
        }
        for (ResourceItem alternative : item.getAlternatives()) {
            if (hasNonVersionQualifiers(alternative)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonVersionQualifiers(@NonNull ResourceItem item) {
        ResourceFile source = item.getSource();
        if (source == null) {
            return false;
        }
        File file = source.getFile();
        if (file == null) {
            return false;
        }
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        FolderConfiguration config = FolderConfiguration.getConfigForFolder(parent.getName());
        if (config == null) {
            return false;
        }
        if (config.getVersionQualifier() != null && config.size() == 1) {
            return false;
        }
        return config.size() > 0;
    }
}