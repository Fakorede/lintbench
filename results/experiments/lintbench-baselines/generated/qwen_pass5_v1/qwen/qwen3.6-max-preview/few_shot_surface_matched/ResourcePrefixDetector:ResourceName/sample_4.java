package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)));

    private String prefix;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        Project project = context.getProject();
        prefix = project.getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        prefix = null;
    }

    @Override
    public void beforeCheckFile(Context context) {
        // Overridden as specified; no per-file initialization required.
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr != null) {
            String name = nameAttr.getValue();
            if (name != null && !name.startsWith(prefix)) {
                context.report(ISSUE, nameAttr, context.getLocation(nameAttr),
                        "Resource name `" + name + "` does not start with the project's resource prefix `" + prefix + "`");
            }
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES || folderType == null) {
            return;
        }

        String fileName = context.file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        if (!baseName.startsWith(prefix)) {
            context.report(ISSUE, Location.create(context.file),
                    "Resource file `" + fileName + "` does not start with the project's resource prefix `" + prefix + "`");
        }
    }
}