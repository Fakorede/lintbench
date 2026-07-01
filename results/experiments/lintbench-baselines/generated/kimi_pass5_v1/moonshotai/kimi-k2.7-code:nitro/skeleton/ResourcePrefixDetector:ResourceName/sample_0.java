package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
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
                    "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. "
                            + "This makes it easier to ensure that you don't accidentally combine resources from different libraries, "
                            + "since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String TAG_RESOURCES = "resources";
    private static final String TAG_ITEM = "item";
    private static final String VALUES_FOLDER_PREFIX = "values";
    private static final String ANDROID_MANIFEST = "AndroidManifest.xml";

    private String mPrefix;
    private boolean mCheck;

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mPrefix = context.getProject().getResourcePrefix();
        mCheck = mPrefix != null && !mPrefix.isEmpty();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
        mCheck = false;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (!mCheck) {
            return;
        }

        File file = context.getFile();
        String name = file.getName();
        if (ANDROID_MANIFEST.equals(name)) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String folderName = parent.getName();
        if (folderName.startsWith(VALUES_FOLDER_PREFIX)) {
            return;
        }

        int dot = name.indexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }

        if (name.startsWith(mPrefix)) {
            return;
        }

        context.report(
                ISSUE,
                file,
                context.getLocation(file),
                String.format("Expected resource `%1$s` to use prefix `%2$s`", name, mPrefix));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mCheck) {
            return;
        }

        String tag = element.getTagName();
        if (TAG_RESOURCES.equals(tag)) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty() || name.startsWith(mPrefix)) {
            return;
        }

        String resourceType = tag;
        if (TAG_ITEM.equals(tag)) {
            resourceType = element.getAttribute(ATTR_TYPE);
        }
        if (resourceType == null || resourceType.isEmpty()) {
            resourceType = "resource";
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                String.format("Expected %1$s resource to start with %2$s prefix", resourceType, mPrefix));
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (!mCheck) {
            return;
        }

        String name = context.getResourceName();
        if (name == null || name.startsWith(mPrefix)) {
            return;
        }

        context.report(
                ISSUE,
                context.getFile(),
                context.getLocation(context.getFile()),
                String.format(
                        "Expected resource `%1$s` in folder `%2$s` to use prefix `%3$s`",
                        name, context.getFolderName(), mPrefix));
    }
}