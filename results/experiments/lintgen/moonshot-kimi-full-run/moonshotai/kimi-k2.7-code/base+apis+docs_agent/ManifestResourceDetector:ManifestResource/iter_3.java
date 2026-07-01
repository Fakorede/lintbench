package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.ResourceReference;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ManifestResourceDetector extends Detector implements XmlScanner {
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_MANIFEST = "manifest";
    private static final String ATTR_LABEL = "label";
    private static final String ATTR_ICON = "icon";
    private static final String ATTR_VERSION_NAME = "versionName";
    private static final String ATTR_VERSION_CODE = "versionCode";

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot "
                    + "vary across configurations (except as a special case, by version, and "
                    + "except for a few specific package attributes such as the application "
                    + "title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestResourceDetector.class, EnumSet.of(Scope.MANIFEST)));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(XmlScannerConstants.ATTR_ALL);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isCreate() || url.isTheme()) {
            return;
        }

        ResourceReference reference = ResourceReference.create(url);
        if (reference == null) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        String tag = owner.getLocalName();
        String name = attribute.getLocalName();
        if (isExempt(tag, name)) {
            return;
        }

        if (hasNonVersionVariants(context, reference)) {
            String message = String.format(
                    "The attribute %1$s in the manifest cannot reference a resource that varies "
                            + "across configurations (except as a special case, by version, and "
                            + "except for the application title and icon)",
                    attribute.getName());
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
        }
    }

    private boolean isExempt(String tag, String attrName) {
        if (TAG_APPLICATION.equals(tag)
                && (ATTR_LABEL.equals(attrName) || ATTR_ICON.equals(attrName))) {
            return true;
        }
        return TAG_MANIFEST.equals(tag)
                && (ATTR_VERSION_NAME.equals(attrName) || ATTR_VERSION_CODE.equals(attrName));
    }

    private boolean hasNonVersionVariants(
            @NonNull XmlContext context, @NonNull ResourceReference reference) {
        LintClient client = context.getClient();
        Project project = context.getMainProject();
        List<File> files = reference.getFiles(client, project);
        if (files == null || files.size() <= 1) {
            return false;
        }

        for (File file : files) {
            File parent = file.getParentFile();
            if (parent == null) {
                continue;
            }
            String folderName = parent.getName();
            FolderConfiguration config = FolderConfiguration.getConfigForFolder(folderName);
            if (config == null || config.isDefault()) {
                continue;
            }
            if (!isVersionOnly(config)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVersionOnly(@NonNull FolderConfiguration config) {
        boolean hasVersion = false;
        for (ResourceQualifier qualifier : config.getQualifiers()) {
            if (qualifier != null && qualifier.isValid()) {
                if (qualifier instanceof VersionQualifier) {
                    hasVersion = true;
                } else {
                    return false;
                }
            }
        }
        return hasVersion;
    }
}