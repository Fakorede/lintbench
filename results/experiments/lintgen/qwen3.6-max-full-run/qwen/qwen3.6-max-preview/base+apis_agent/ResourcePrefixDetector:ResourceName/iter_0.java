package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.List;

public class ResourcePrefixDetector extends Detector implements ResourceFolderScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void visitFolder(@NonNull Context context, @NonNull File folder, @NonNull List<File> files) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String folderName = folder.getName();
        if (folderName.startsWith(SdkConstants.FD_RES_VALUES)) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            int dot = name.indexOf('.');
            String baseName = dot > 0 ? name.substring(0, dot) : name;
            if (!baseName.startsWith(prefix)) {
                context.report(ISSUE, Location.create(file),
                        "Resource does not start with required prefix \"" + prefix + "\"");
            }
        }
    }

    @Override
    public boolean appliesTo(@NonNull String folderName, @NonNull File file) {
        return folderName.startsWith(SdkConstants.FD_RES_VALUES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String tag = element.getTagName();
        if (tag.equals("resources") || tag.equals("eat-comment") || tag.equals("skip")) {
            return;
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isEmpty() && !name.startsWith(prefix)) {
            Attr nameAttr = element.getAttributeNode("name");
            context.report(ISSUE, nameAttr, context.getLocation(nameAttr),
                    "Resource does not start with required prefix \"" + prefix + "\"");
        }
    }
}