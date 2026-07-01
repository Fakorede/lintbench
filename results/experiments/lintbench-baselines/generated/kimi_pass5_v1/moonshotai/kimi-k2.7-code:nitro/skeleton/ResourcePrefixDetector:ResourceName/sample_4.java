package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STYLE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector
        implements XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    ResourcePrefixDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure "
                            + "that you do not accidentally combine resources from different "
                            + "libraries, since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Nullable private String mPrefix;
    private boolean mHavePrefix;

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        mPrefix = project.getResourcePrefix();
        mHavePrefix = mPrefix != null && !mPrefix.isEmpty();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
        mHavePrefix = false;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (!mHavePrefix) {
            return;
        }

        if (!(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        ResourceFolderType folderType =
                ResourceFolderType.getFolderType(xmlContext.getFolderName());
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        String baseName = stripExtension(xmlContext.file.getName());
        if (!baseName.startsWith(mPrefix)) {
            reportViolation(xmlContext, Location.create(xmlContext.file), mPrefix);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mHavePrefix) {
            return;
        }

        String tagName = element.getTagName();
        if (tagName.equals(TAG_RESOURCES) || tagName.equals(TAG_STYLE)) {
            return;
        }

        if (tagName.equals(TAG_ITEM)) {
            if (element.getParentNode() instanceof Element) {
                Element parent = (Element) element.getParentNode();
                if (parent.getTagName().equals(TAG_STYLE)) {
                    return;
                }
            }
        }

        if (!element.hasAttribute(ATTR_NAME)) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        if (!name.startsWith(mPrefix)) {
            reportViolation(context, context.getLocation(element), mPrefix);
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (!mHavePrefix) {
            return;
        }

        ResourceType type = context.getResourceType();
        if (type == null || type == ResourceType.ID) {
            return;
        }

        String name = context.getName();
        if (name != null && !name.startsWith(mPrefix)) {
            reportViolation(context, Location.create(context.file), mPrefix);
        }
    }

    @NonNull
    private static String stripExtension(@NonNull String fileName) {
        int dot = fileName.indexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    private static void reportViolation(
            @NonNull Context context,
            @NonNull Location location,
            @NonNull String prefix) {
        context.report(
                ISSUE,
                location,
                String.format(
                        "The resource prefix is `%1$s`; the resource name should start with `%1$s`",
                        prefix));
    }
}