package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    ResourcePrefixDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure that you don't "
                            + "accidentally combine resources from different libraries, since they all end "
                            + "up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /** The current resource prefix, or null if not set or not applicable */
    @Nullable private String mPrefix;

    /** Whether the current file is a values file */
    private boolean mIsValuesFile;

    public ResourcePrefixDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                // Values file elements
                "string",
                "string-array",
                "plurals",
                "integer",
                "integer-array",
                "bool",
                "dimen",
                "color",
                "style",
                "declare-styleable",
                "attr",
                "drawable",
                "item",
                "fraction",
                "array",
                "layout",
                "menu",
                "id",
                "animator",
                "anim",
                "interpolator",
                "transition",
                "xml",
                "raw",
                "mipmap",
                "navigation",
                "font");
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        Project project = context.getMainProject();
        mPrefix = project.getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mPrefix == null) {
            return;
        }

        // Check if this is a values file
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            ResourceFolderType folderType = xmlContext.getResourceFolderType();
            mIsValuesFile = folderType == ResourceFolderType.VALUES;

            // For non-values files, check the filename itself
            if (folderType != null && folderType != ResourceFolderType.VALUES) {
                String fileName = context.file.getName();
                // Strip extension
                int dotIndex = fileName.lastIndexOf('.');
                String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
                if (!resourceName.startsWith(mPrefix)) {
                    String message =
                            String.format(
                                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s`",
                                    resourceName,
                                    mPrefix,
                                    mPrefix + resourceName);
                    xmlContext.report(ISSUE, xmlContext.getLocation(xmlContext.document.getDocumentElement()), message);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null || !mIsValuesFile) {
            return;
        }

        // In values files, resources are named via the "name" attribute
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Skip tools namespace items and private/internal resources
        if (name.startsWith("android:")) {
            return;
        }

        // Check if the resource name starts with the required prefix
        if (!name.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s`",
                            name,
                            mPrefix,
                            mPrefix + name);

            Attr nameAttr = element.getAttributeNode("name");
            if (nameAttr != null) {
                context.report(ISSUE, nameAttr, context.getValueLocation(nameAttr), message);
            } else {
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefix == null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File file = context.file;
        String fileName = file.getName();
        // Strip extension
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        if (!resourceName.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s`",
                            resourceName,
                            mPrefix,
                            mPrefix + resourceName);
            context.report(ISSUE, context.getLocation(file), message);
        }
    }
}