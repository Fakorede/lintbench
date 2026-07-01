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

    /** Whether the current file should be checked (based on whether it's in a prefixed project) */
    private boolean mEnabled;

    public ResourcePrefixDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                // Value resources
                "string",
                "string-array",
                "plurals",
                "color",
                "dimen",
                "drawable",
                "bool",
                "integer",
                "integer-array",
                "style",
                "declare-styleable",
                "attr",
                "item",
                "array",
                "fraction",
                "layout",
                "menu",
                "id",
                "public");
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        mPrefix = project.getResourcePrefix();
        mEnabled = mPrefix != null;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
        mEnabled = false;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (!mEnabled) {
            return;
        }

        // For layout, menu, drawable, etc. files (non-value resource files),
        // check the file name itself
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            ResourceFolderType folderType = xmlContext.getResourceFolderType();
            if (folderType != null && folderType != ResourceFolderType.VALUES) {
                // The resource name is the file name without extension
                String fileName = context.file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
                checkResourceName(context, resourceName, folderType.getName(), context.file);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mEnabled) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            // Non-value files: resource name is the filename, handled in beforeCheckFile
            return;
        }

        // For value resources, look for the "name" attribute
        String tagName = element.getTagName();

        // Handle <item> elements which need a type attribute
        if (tagName.equals("item")) {
            String type = element.getAttribute("type");
            if (type == null || type.isEmpty()) {
                return;
            }
        }

        // Get the name attribute
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Strip any style parent reference (e.g. "ParentStyle.MyStyle" -> check "MyStyle" or full?)
        // Actually for styles, the name can contain a dot for implicit parent, but we check the full name
        checkResourceName(context, name, tagName, element);
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (!mEnabled) {
            return;
        }

        // For binary resources (e.g. images in drawable-xxx), check the file name
        String fileName = context.file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        ResourceFolderType folderType = context.getResourceFolderType();
        String folderName = folderType != null ? folderType.getName() : "binary";

        checkResourceName(context, resourceName, folderName, context.file);
    }

    /**
     * Checks that the given resource name starts with the required prefix.
     *
     * @param context the context to report issues to
     * @param name the resource name to check
     * @param type the resource type (for the error message)
     * @param locationNode the node to use for the error location (Element or File)
     */
    private void checkResourceName(
            @NonNull Context context,
            @NonNull String name,
            @NonNull String type,
            @NonNull Object locationNode) {
        if (mPrefix == null) {
            return;
        }

        // Allow names that already start with the prefix
        if (name.startsWith(mPrefix)) {
            return;
        }

        // Also allow names that start with android: namespace prefix
        if (name.startsWith("android:")) {
            return;
        }

        String message =
                String.format(
                        "Resource named '%1$s' does not start with the project's resource prefix '%2$s'; "
                                + "rename to '%3$s%1$s'?",
                        name, mPrefix, mPrefix);

        if (locationNode instanceof Element && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            Element element = (Element) locationNode;
            // Try to find the name attribute for better location
            Attr nameAttr = element.getAttributeNode("name");
            if (nameAttr != null) {
                xmlContext.report(ISSUE, nameAttr, xmlContext.getValueLocation(nameAttr), message);
            } else {
                xmlContext.report(ISSUE, element, xmlContext.getLocation(element), message);
            }
        } else if (locationNode instanceof File) {
            context.report(ISSUE, context.getLocation(context.file), message);
        }
    }
}