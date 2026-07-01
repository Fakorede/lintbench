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
import org.w3c.dom.NamedNodeMap;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ATTR;
import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;
import static com.android.SdkConstants.TAG_EAT_COMMENT;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_SKIP;

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

    /** The current resource prefix, or null if we are not in a Gradle project or no prefix set */
    @Nullable private String mPrefix;

    /** Whether the current file is a values file */
    private boolean mIsValuesFile;

    /** The current resource folder type */
    @Nullable private ResourceFolderType mFolderType;

    public ResourcePrefixDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_ATTR,
                TAG_DECLARE_STYLEABLE,
                TAG_ITEM,
                TAG_RESOURCES,
                "anim",
                "animator",
                "bool",
                "color",
                "dimen",
                "drawable",
                "fraction",
                "id",
                "integer",
                "interpolator",
                "layout",
                "menu",
                "mipmap",
                "plurals",
                "raw",
                "string",
                "style",
                "xml");
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        if (project.isGradleProject()) {
            String prefix = project.getResourcePrefix();
            mPrefix = prefix;
        } else {
            mPrefix = null;
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
        mFolderType = null;
        mIsValuesFile = false;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mPrefix == null) {
            return;
        }

        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            mFolderType = xmlContext.getResourceFolderType();
            mIsValuesFile = mFolderType == ResourceFolderType.VALUES;

            // For non-values files, check the file name itself
            if (!mIsValuesFile && mFolderType != null) {
                String fileName = context.file.getName();
                // Strip extension
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex != -1) {
                    fileName = fileName.substring(0, dotIndex);
                }
                if (!fileName.startsWith(mPrefix)) {
                    String message =
                            String.format(
                                    "Resource named '%1$s' does not start with the project's resource prefix '%2$s'; rename to '%3$s%1$s'?",
                                    fileName, mPrefix, mPrefix);
                    xmlContext.report(ISSUE, xmlContext.getLocation(xmlContext.document.getDocumentElement()), message);
                }
            }
        } else if (context instanceof ResourceContext) {
            ResourceContext resourceContext = (ResourceContext) context;
            mFolderType = resourceContext.getResourceFolderType();
            mIsValuesFile = false;

            // For binary resources, check the file name
            String fileName = context.file.getName();
            // Strip extension
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex != -1) {
                fileName = fileName.substring(0, dotIndex);
            }
            if (!fileName.startsWith(mPrefix)) {
                String message =
                        String.format(
                                "Resource named '%1$s' does not start with the project's resource prefix '%2$s'; rename to '%3$s%1$s'?",
                                fileName, mPrefix, mPrefix);
                context.report(ISSUE, resourceContext.getLocation(), message);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null || !mIsValuesFile) {
            return;
        }

        String tag = element.getTagName();

        // Skip container elements
        if (TAG_RESOURCES.equals(tag) || TAG_EAT_COMMENT.equals(tag) || TAG_SKIP.equals(tag)) {
            return;
        }

        // For declare-styleable, check the name attribute and also check child attr elements
        if (TAG_DECLARE_STYLEABLE.equals(tag)) {
            checkNameAttribute(context, element);
            return;
        }

        // For attr elements inside declare-styleable, check if they have a name
        if (TAG_ATTR.equals(tag)) {
            // Check if the parent is declare-styleable; if so, we check the attr name
            // but only if the attr is defined here (not referenced)
            String name = element.getAttribute(ATTR_NAME);
            if (name != null && !name.isEmpty() && !name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                // Only check top-level attrs, not those inside declare-styleable
                org.w3c.dom.Node parent = element.getParentNode();
                if (parent instanceof Element) {
                    String parentTag = ((Element) parent).getTagName();
                    if (TAG_DECLARE_STYLEABLE.equals(parentTag)) {
                        // attr inside declare-styleable: check its name
                        checkNameAttribute(context, element);
                        return;
                    }
                }
                checkNameAttribute(context, element);
            }
            return;
        }

        // For item elements, check if they have a name attribute
        if (TAG_ITEM.equals(tag)) {
            checkNameAttribute(context, element);
            return;
        }

        // For other resource elements (string, dimen, color, etc.), check name
        checkNameAttribute(context, element);
    }

    private void checkNameAttribute(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Skip names that start with android: prefix
        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
            return;
        }

        // For styles, the name may contain a dot-separated parent reference (e.g., "MyStyle.Title")
        // We check the base name (before any dots)
        String baseName = name;
        int dotIndex = name.indexOf('.');
        if (dotIndex != -1) {
            baseName = name.substring(0, dotIndex);
        }

        if (!baseName.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named '%1$s' does not start with the project's resource prefix '%2$s'; rename to '%3$s%1$s'?",
                            name, mPrefix, mPrefix);
            context.report(ISSUE, nameAttr, context.getValueLocation(nameAttr), message);
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefix == null) {
            return;
        }

        String fileName = context.file.getName();
        // Strip extension
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            fileName = fileName.substring(0, dotIndex);
        }

        if (!fileName.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named '%1$s' does not start with the project's resource prefix '%2$s'; rename to '%3$s%1$s'?",
                            fileName, mPrefix, mPrefix);
            context.report(ISSUE, context.getLocation(), message);
        }
    }
}