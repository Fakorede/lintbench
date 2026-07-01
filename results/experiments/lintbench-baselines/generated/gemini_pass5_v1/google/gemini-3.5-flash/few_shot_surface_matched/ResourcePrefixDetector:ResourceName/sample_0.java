package com.android.tools.lint.checks;

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

public class ResourcePrefixDetector extends Detector implements XmlScanner, com.android.tools.lint.detector.api.BinaryResourceScanner {

    public static final Implementation IMPLEMENTATION = new Implementation(
            ResourcePrefixDetector.class,
            java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)
    );

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources "
                    + "in the project must conform to. This makes it easier to ensure that "
                    + "you don't accidentally combine resources from different libraries, "
                    + "since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            IMPLEMENTATION
    );

    private String prefix;

    @Override
    public void beforeCheckEachProject(Context context) {
        prefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        prefix = null;
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        com.android.resources.ResourceFolderType folderType = context.getFolderType();
        if (folderType != null && folderType != com.android.resources.ResourceFolderType.VALUES) {
            String name = context.file.getName();
            int dot = name.indexOf('.');
            String baseName = dot != -1 ? name.substring(0, dot) : name;
            if (!baseName.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        "The resource `" + baseName + "` must begin with the prefix `" + prefix + "`"
                );
            }
        }
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        org.w3c.dom.Node parent = element.getParentNode();
        if (parent != null && "resources".equals(parent.getNodeName())) {
            String name = element.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                if (!name.startsWith(prefix)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "The resource `" + name + "` must begin with the prefix `" + prefix + "`"
                    );
                }
            }
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        com.android.resources.ResourceFolderType folderType = context.getFolderType();
        if (folderType != null && folderType != com.android.resources.ResourceFolderType.VALUES) {
            String name = context.file.getName();
            int dot = name.indexOf('.');
            String baseName = dot != -1 ? name.substring(0, dot) : name;
            if (!baseName.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        "The resource `" + baseName + "` must begin with the prefix `" + prefix + "`"
                );
            }
        }
    }
}