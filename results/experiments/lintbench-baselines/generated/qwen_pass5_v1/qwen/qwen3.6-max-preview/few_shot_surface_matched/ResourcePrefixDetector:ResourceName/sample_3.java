package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. " +
            "This makes it easier to ensure that you don't accidentally combine resources from different libraries, " +
            "since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE))
    );

    private String resourcePrefix;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        resourcePrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        resourcePrefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (resourcePrefix == null || resourcePrefix.isEmpty()) return;
        if (!(context instanceof XmlContext)) return;

        XmlContext xmlContext = (XmlContext) context;
        ResourceFolderType folderType = xmlContext.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES || folderType == ResourceFolderType.RAW) return;

        File file = context.file;
        String parent = file.getParentFile() != null ? file.getParentFile().getName() : "";
        if (!isResourceFolder(parent)) return;

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String baseName = dot > 0 ? name.substring(0, dot) : name;

        if (!baseName.startsWith(resourcePrefix)) {
            context.report(ISSUE, Location.create(file),
                    "Resource file name must start with the required prefix '" + resourcePrefix + "'");
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (resourcePrefix == null || resourcePrefix.isEmpty()) return;
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) return;

        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) return;

        String name = nameAttr.getValue();
        if (name.isEmpty()) return;

        if (!name.startsWith(resourcePrefix)) {
            context.report(ISSUE, nameAttr, context.getLocation(nameAttr),
                    "Resource name must start with the required prefix '" + resourcePrefix + "'");
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (resourcePrefix == null || resourcePrefix.isEmpty()) return;

        File file = context.file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String baseName = dot > 0 ? name.substring(0, dot) : name;

        if (!baseName.startsWith(resourcePrefix)) {
            context.report(ISSUE, Location.create(file),
                    "Resource file name must start with the required prefix '" + resourcePrefix + "'");
        }
    }

    private static boolean isResourceFolder(String folderName) {
        return folderName.startsWith("drawable") || folderName.startsWith("layout") ||
               folderName.startsWith("menu") || folderName.startsWith("anim") ||
               folderName.startsWith("animator") || folderName.startsWith("color") ||
               folderName.startsWith("mipmap") || folderName.startsWith("xml") ||
               folderName.startsWith("navigation") || folderName.startsWith("transition") ||
               folderName.startsWith("interpolator") || folderName.startsWith("font");
    }
}