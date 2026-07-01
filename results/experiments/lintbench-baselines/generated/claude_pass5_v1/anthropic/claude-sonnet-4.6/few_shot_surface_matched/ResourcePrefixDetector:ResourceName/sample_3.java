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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure that "
                            + "you don't accidentally combine resources from different libraries, "
                            + "since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)));

    /** The current resource prefix, or null if there is none or it is not a Gradle project. */
    @Nullable private String mPrefix;

    /** Whether the current file should be checked (i.e., prefix is set). */
    private boolean mEnabled;

    public ResourcePrefixDetector() {}

    // ---- XmlScanner ----

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        mPrefix = project.getResourcePrefix();
        mEnabled = mPrefix != null && !mPrefix.isEmpty();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
        mEnabled = false;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Nothing to do here; enabled state is already set in beforeCheckEachProject.
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                // Value resource elements
                "color",
                "dimen",
                "drawable",
                "string",
                "style",
                "array",
                "string-array",
                "integer-array",
                "plurals",
                "integer",
                "bool",
                "fraction",
                "attr",
                "declare-styleable",
                "id",
                "layout",
                "menu",
                "raw",
                "anim",
                "animator",
                "interpolator",
                "transition",
                "navigation",
                "xml",
                "font",
                "mipmap",
                "item");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mEnabled) {
            return;
        }

        // Get the resource name from the "name" attribute
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Strip off style parent references (e.g., "ParentStyle.ChildName")
        // and check only the leaf name for styles; for other resources check the full name.
        if (!name.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named '`%1$s`' does not start with the project's resource"
                                    + " prefix '`%2$s`'; rename to '`%3$s`'",
                            name, mPrefix, mPrefix + name);
            context.report(ISSUE, nameAttr, context.getValueLocation(nameAttr), message);
        }
    }

    // ---- BinaryResourceScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        if (!mEnabled) {
            return false;
        }
        // Binary resources live in drawable, mipmap, raw, etc.
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.RAW
                || folderType == ResourceFolderType.ANIM
                || folderType == ResourceFolderType.ANIMATOR
                || folderType == ResourceFolderType.INTERPOLATOR
                || folderType == ResourceFolderType.TRANSITION
                || folderType == ResourceFolderType.FONT
                || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.MENU
                || folderType == ResourceFolderType.XML
                || folderType == ResourceFolderType.NAVIGATION
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (!mEnabled) {
            return;
        }

        String fileName = context.file.getName();
        // Strip the extension to get the resource name
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        if (resourceName.isEmpty()) {
            return;
        }

        if (!resourceName.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named '`%1$s`' does not start with the project's resource"
                                    + " prefix '`%2$s`'; rename to '`%3$s`'",
                            resourceName, mPrefix, mPrefix + resourceName);
            context.report(ISSUE, context.getLocation(context.file), message);
        }
    }
}