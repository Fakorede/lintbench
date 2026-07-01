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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

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
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)));

    private static final String ATTR_NAME = "name";
    private static final String ATTR_ID = "id";
    private static final String NEW_ID_PREFIX = "@+id/";
    private static final String ID_PREFIX = "@id/";

    /** The current resource prefix, or null if not in a Gradle project or no prefix set. */
    private String mPrefix;

    /** Whether we are currently skipping the file (e.g. prefix not set). */
    private boolean mSkipFile;

    public ResourcePrefixDetector() {
    }

    // ---- Implements Detector ----

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
        mSkipFile = mPrefix == null || mPrefix.isEmpty();
    }

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mSkipFile) {
            return;
        }

        // For value resource files, the name attribute on top-level elements defines
        // the resource name (e.g. <string name="app_name">, <color name="primary">, etc.)
        // For layout/drawable/etc files, the file name itself is the resource name,
        // but we also need to check id declarations within the file.

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            // Check the name attribute of the element
            String name = element.getAttribute(ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                checkResourceName(context, element, name);
            }
        }

        // Check all android:id attributes in any resource file
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attr = (Attr) attributes.item(i);
                String attrName = attr.getLocalName();
                if (ATTR_ID.equals(attrName)) {
                    String value = attr.getValue();
                    if (value != null) {
                        String idName = null;
                        if (value.startsWith(NEW_ID_PREFIX)) {
                            idName = value.substring(NEW_ID_PREFIX.length());
                        } else if (value.startsWith(ID_PREFIX)) {
                            idName = value.substring(ID_PREFIX.length());
                        }
                        if (idName != null && !idName.isEmpty()) {
                            if (!idName.startsWith(mPrefix)) {
                                String message = String.format(
                                        "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s%1$s`?",
                                        idName, mPrefix, mPrefix);
                                context.report(ISSUE, attr, context.getLocation(attr), message);
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkResourceName(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String name) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }
        if (!name.startsWith(mPrefix)) {
            // Get the name attribute node for better location reporting
            Attr nameAttr = element.getAttributeNode(ATTR_NAME);
            String message = String.format(
                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s%1$s`?",
                    name, mPrefix, mPrefix);
            if (nameAttr != null) {
                context.report(ISSUE, nameAttr, context.getValueLocation(nameAttr), message);
            } else {
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        // Apply to all resource folder types for binary resources
        return true;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
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
            String message = String.format(
                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s%1$s`?",
                    resourceName, mPrefix, mPrefix);
            context.report(ISSUE, context.getLocation(file), message);
        }
    }
}