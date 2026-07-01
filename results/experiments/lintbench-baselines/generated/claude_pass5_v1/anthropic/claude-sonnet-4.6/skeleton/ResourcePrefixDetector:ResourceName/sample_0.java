package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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

    /** The current resource prefix, if any */
    @Nullable private String mPrefix;

    /** Whether the current file is in a values folder */
    private boolean mIsValuesFile;

    public ResourcePrefixDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                // Values file elements that declare resources
                "string",
                "string-array",
                "plurals",
                "integer",
                "integer-array",
                "bool",
                "dimen",
                "color",
                "array",
                "declare-styleable",
                "style",
                "attr",
                "item",
                "drawable");
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
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

        // Determine if we are in a values folder
        mIsValuesFile = false;
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            ResourceFolderType folderType = xmlContext.getResourceFolderType();
            if (folderType == ResourceFolderType.VALUES) {
                mIsValuesFile = true;
            } else if (folderType != null) {
                // For non-values resource files, the file name itself is the resource name
                String fileName = context.file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String resourceName = dotIndex != -1 ? fileName.substring(0, dotIndex) : fileName;
                if (!resourceName.startsWith(mPrefix)) {
                    String message =
                            String.format(
                                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s%1$s`?",
                                    resourceName, mPrefix, mPrefix);
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

        // Get the name attribute of the element
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // For styles, we allow names like "ParentStyle.ChildStyle" where only the root
        // needs the prefix, or the name itself starts with the prefix
        if (!name.startsWith(mPrefix)) {
            // Check if this is a style that extends another style using dot notation
            // e.g., "AppTheme.MyStyle" - in this case AppTheme might have the prefix
            String tagName = element.getTagName();
            if ("style".equals(tagName)) {
                // For styles using dot notation inheritance, check if any prefix segment matches
                int dotIndex = name.indexOf('.');
                if (dotIndex != -1) {
                    String rootName = name.substring(0, dotIndex);
                    if (rootName.startsWith(mPrefix)) {
                        return;
                    }
                }
            }

            Attr nameAttr = element.getAttributeNode("name");
            String message =
                    String.format(
                            "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s%1$s`?",
                            name, mPrefix, mPrefix);
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
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            File file = context.file;
            String fileName = file.getName();
            int dotIndex = fileName.lastIndexOf('.');
            String resourceName = dotIndex != -1 ? fileName.substring(0, dotIndex) : fileName;
            if (!resourceName.startsWith(mPrefix)) {
                String message =
                        String.format(
                                "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s%1$s`?",
                                resourceName, mPrefix, mPrefix);
                context.report(ISSUE, context.getLocation(file), message);
            }
        }
    }
}