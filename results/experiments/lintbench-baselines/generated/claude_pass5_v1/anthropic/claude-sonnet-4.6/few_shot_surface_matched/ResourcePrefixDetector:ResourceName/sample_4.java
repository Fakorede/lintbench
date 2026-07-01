package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

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

    private String mPrefix;
    private String mCurrentFolderPrefix;

    public ResourcePrefixDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mPrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCurrentFolderPrefix = null;
        if (mPrefix == null) {
            return;
        }
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            ResourceFolderType folderType = xmlContext.getResourceFolderType();
            if (folderType != null && folderType != ResourceFolderType.VALUES) {
                // For file-based resources (layout, drawable, etc.), the resource name
                // is the file name itself. We'll check in beforeCheckFile.
                String fileName = context.file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
                if (!resourceName.startsWith(mPrefix)) {
                    // We'll report this via visitElement for the root element,
                    // or handle it here. We store for later reporting.
                    mCurrentFolderPrefix = resourceName;
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            // For value resources, check the "name" attribute of resource declarations
            String tagName = element.getTagName();
            if (isResourceDeclarationTag(tagName)) {
                String nameAttr = element.getAttributeNS(null, SdkConstants.ATTR_NAME);
                if (nameAttr == null || nameAttr.isEmpty()) {
                    nameAttr = element.getAttribute(SdkConstants.ATTR_NAME);
                }
                if (nameAttr != null && !nameAttr.isEmpty()) {
                    // Strip style parent references (e.g. "ParentStyle.MyStyle" -> check "MyStyle"
                    // or check the full name)
                    String resourceName = nameAttr;
                    // For styles, the name may contain a dot for parent reference
                    // We check the base name (after last dot for style inheritance)
                    // Actually, the convention is to check the full name for prefix
                    if (!resourceName.startsWith(mPrefix)) {
                        Attr nameAttribute = element.getAttributeNode(SdkConstants.ATTR_NAME);
                        if (nameAttribute == null) {
                            nameAttribute = element.getAttributeNodeNS(null, SdkConstants.ATTR_NAME);
                        }
                        String message = getErrorMessage(resourceName, mPrefix);
                        if (nameAttribute != null) {
                            context.report(ISSUE, element, context.getValueLocation(nameAttribute),
                                    message);
                        } else {
                            context.report(ISSUE, element, context.getLocation(element), message);
                        }
                    }
                }
            }
        } else {
            // For file-based resources, only check on root element
            if (element.getParentNode() == null
                    || element.getParentNode().getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                // This is the root element
                if (mCurrentFolderPrefix != null) {
                    String resourceName = mCurrentFolderPrefix;
                    String message = getErrorMessage(resourceName, mPrefix);
                    context.report(ISSUE, element, context.getLocation(element), message);
                    // Reset so we don't report multiple times
                    mCurrentFolderPrefix = null;
                }
            }
        }
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefix == null) {
            return;
        }

        File file = context.file;
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        if (!resourceName.startsWith(mPrefix)) {
            String message = getErrorMessage(resourceName, mPrefix);
            context.report(ISSUE, context.getLocation(file), message);
        }
    }

    // ---- Helper methods ----

    private static boolean isResourceDeclarationTag(@NonNull String tagName) {
        switch (tagName) {
            case SdkConstants.TAG_STRING:
            case SdkConstants.TAG_PLURALS:
            case "string-array":
            case "integer-array":
            case "array":
            case "color":
            case "dimen":
            case "integer":
            case "bool":
            case "fraction":
            case "attr":
            case "declare-styleable":
            case "style":
            case "item":
            case "drawable":
            case "id":
                return true;
            default:
                return false;
        }
    }

    @NonNull
    private static String getErrorMessage(@NonNull String resourceName, @NonNull String prefix) {
        return "Resource named `" + resourceName + "` does not start with the project's resource"
                + " prefix `" + prefix + "`; rename to `" + prefix + resourceName + "`?";
    }
}