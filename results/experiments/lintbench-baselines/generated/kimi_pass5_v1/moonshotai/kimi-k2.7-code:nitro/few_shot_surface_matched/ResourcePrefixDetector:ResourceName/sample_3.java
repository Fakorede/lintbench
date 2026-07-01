package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
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

    public static final Issue RESOURCE_NAME =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. "
                            + "This makes it easier to ensure that you don't accidentally combine resources from different libraries, "
                            + "since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.BINARY_RESOURCE_FILE)));

    private String mPrefix;

    @Override
    public void beforeCheckEachProject(Context context) {
        mPrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        mPrefix = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("item", "public", "declare-styleable", "attr");
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        java.io.File file = context.file;
        java.io.File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        ResourceFolderType type = ResourceFolderType.getFolderType(parent.getName());
        if (type == null || type == ResourceFolderType.VALUES || type == ResourceFolderType.RAW) {
            return;
        }

        String name = stripExtension(file.getName());
        if (!name.startsWith(mPrefix)) {
            context.report(
                    RESOURCE_NAME,
                    Location.create(file),
                    "Resource named `" + name + "` does not start with the resource prefix `" + mPrefix + "`");
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        Attr attr = element.getAttributeNode("name");
        if (attr == null) {
            return;
        }

        String name = attr.getValue();
        if (name == null || name.isEmpty() || name.startsWith(mPrefix)) {
            return;
        }

        context.report(
                RESOURCE_NAME,
                attr,
                context.getValueLocation(attr),
                "Resource named `" + name + "` does not start with the resource prefix `" + mPrefix + "`");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType != ResourceFolderType.VALUES && folderType != ResourceFolderType.RAW;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        String name = stripExtension(context.file.getName());
        if (!name.startsWith(mPrefix)) {
            context.report(
                    RESOURCE_NAME,
                    Location.create(context.file),
                    "Resource named `" + name + "` does not start with the resource prefix `" + mPrefix + "`");
        }
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index == -1 ? fileName : fileName.substring(0, index);
    }
}