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

    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";
    private static final String TAG_EAT_COMMENT = "eat-comment";
    private static final String TAG_SKIP = "skip";

    @Nullable private String mPrefix;
    private boolean mEnabled;

    public ResourcePrefixDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "attr",
                "declare-styleable",
                "drawable",
                "bool",
                "color",
                "dimen",
                "fraction",
                "integer",
                "string",
                "plurals",
                "array",
                "string-array",
                "integer-array",
                "style",
                "menu",
                "layout",
                "anim",
                "animator",
                "interpolator",
                TAG_ITEM);
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mPrefix = context.getProject().getResourcePrefix();
        mEnabled = mPrefix != null && !mPrefix.isEmpty();
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
        // For file-based resources (layouts, drawables, etc.), check the file name
        if (context instanceof ResourceContext) {
            ResourceContext resourceContext = (ResourceContext) context;
            ResourceFolderType folderType = resourceContext.getResourceFolderType();
            if (folderType != null && isFileBasedResourceType(folderType)) {
                String fileName = context.file.getName();
                // Strip extension
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex != -1) {
                    fileName = fileName.substring(0, dotIndex);
                }
                if (!fileName.startsWith(mPrefix)) {
                    String message =
                            String.format(
                                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s%1$s`?",
                                    fileName, mPrefix, mPrefix);
                    context.report(ISSUE, context.getLocation(context.file), message);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mEnabled) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();

        // For value resources (res/values/), check the name attribute
        if (folderType == ResourceFolderType.VALUES) {
            String tagName = element.getTagName();
            if (TAG_EAT_COMMENT.equals(tagName) || TAG_SKIP.equals(tagName)) {
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

            // Skip theme attributes (android:foo style names)
            if (name.startsWith("android:")) {
                return;
            }

            // For styles, we allow inheritance via dot notation: ParentStyle.ChildStyle
            // but the base name should still start with the prefix
            // We check only the part before the dot if it's a style
            String checkName = name;
            if ("style".equals(tagName)) {
                // For styles that extend another style via dot notation,
                // only check the root name
                int dotIndex = name.indexOf('.');
                if (dotIndex != -1) {
                    checkName = name.substring(0, dotIndex);
                }
            }

            if (!checkName.startsWith(mPrefix)) {
                String message =
                        String.format(
                                "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s%1$s`?",
                                name, mPrefix, mPrefix);
                context.report(ISSUE, context.getValueLocation(nameAttr), message);
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (!mEnabled) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && isFileBasedResourceType(folderType)) {
            String fileName = context.file.getName();
            // Strip extension
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex != -1) {
                fileName = fileName.substring(0, dotIndex);
            }
            if (!fileName.startsWith(mPrefix)) {
                String message =
                        String.format(
                                "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s%1$s`?",
                                fileName, mPrefix, mPrefix);
                context.report(ISSUE, context.getLocation(context.file), message);
            }
        }
    }

    private static boolean isFileBasedResourceType(@NonNull ResourceFolderType folderType) {
        switch (folderType) {
            case VALUES:
                return false;
            case LAYOUT:
            case DRAWABLE:
            case MIPMAP:
            case ANIM:
            case ANIMATOR:
            case INTERPOLATOR:
            case MENU:
            case RAW:
            case XML:
            case COLOR:
            case FONT:
            case TRANSITION:
            case NAVIGATION:
                return true;
            default:
                return true;
        }
    }
}