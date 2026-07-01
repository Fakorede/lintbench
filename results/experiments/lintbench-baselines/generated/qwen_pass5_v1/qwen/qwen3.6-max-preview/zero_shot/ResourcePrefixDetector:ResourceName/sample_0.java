package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class ResourcePrefixDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "ResourceName",
        "Resource with Wrong Prefix",
        "In Gradle projects you can specify a resource prefix that all resources " +
        "in the project must conform to. This makes it easier to ensure that you don't " +
        "accidentally combine resources from different libraries, since they all end " +
        "up in the same shared app namespace.",
        Category.CORRECTNESS,
        7,
        Severity.WARNING,
        new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        // public.xml only declares visibility for existing resources, skip prefix check
        if (context.getFile().getName().equals("public.xml")) {
            return;
        }

        String name = attribute.getValue();
        if (name != null && !name.startsWith(prefix)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                "Resource name does not start with the required prefix \"" + prefix + "\"");
        }
    }

    @Override
    public void visitFile(Context context, File file) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        // Values resources are checked via XML name attributes, not file names
        if (folderType == ResourceFolderType.VALUES || folderType == null) {
            return;
        }

        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        if (!baseName.startsWith(prefix)) {
            context.report(ISSUE, Location.create(file),
                "Resource name does not start with the required prefix \"" + prefix + "\"");
        }
    }
}