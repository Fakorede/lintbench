package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector
        implements Detector.XmlScanner, Detector.ResourceFolderScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. "
                            + "Resources whose names do not start with the configured prefix can accidentally conflict with resources from other libraries.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.RESOURCE_FOLDER)));

    private static final String ATTR_NAME = "name";

    private String mResourcePrefix;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mResourcePrefix = null;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mResourcePrefix =
                context.getProject().getBuildModule() != null
                        ? context.getProject().getBuildModule().getResourcePrefix()
                        : null;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mResourcePrefix == null || mResourcePrefix.isEmpty()) {
            return;
        }

        Attr attr = element.getAttributeNode(ATTR_NAME);
        if (attr == null) {
            return;
        }

        String name = attr.getValue();
        if (name.isEmpty()) {
            return;
        }

        if (!name.startsWith(mResourcePrefix)) {
            context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    String.format(
                            "Resource name `%1$s` does not start with the configured resource prefix `%2$s`",
                            name,
                            mResourcePrefix));
        }
    }

    @Override
    public void checkFolder(
            @NonNull ResourceContext context,
            @NonNull ResourceFolderType folderType,
            @NonNull File folder) {
        if (mResourcePrefix == null || mResourcePrefix.isEmpty()) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }

            String fileName = file.getName();
            if (fileName.startsWith(".")) {
                continue;
            }

            String resourceName = getResourceName(fileName);
            if (resourceName.isEmpty()) {
                continue;
            }

            if (!resourceName.startsWith(mResourcePrefix)) {
                context.report(
                        ISSUE,
                        file,
                        Location.create(file),
                        String.format(
                                "Resource name `%1$s` does not start with the configured resource prefix `%2$s`",
                                resourceName,
                                mResourcePrefix));
            }
        }
    }

    private static String getResourceName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }

        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}