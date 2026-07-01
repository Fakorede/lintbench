package com.android.tools.lint.checks;

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
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. This makes it easier to ensure that you don't accidentally combine resources from different libraries, since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE))
    );

    private String prefix;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

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
        if (prefix == null || prefix.isEmpty()) return;
        if (!(context instanceof XmlContext)) return;

        File file = context.file;
        if (file == null) return;

        File parentDir = file.getParentFile();
        String parentName = parentDir != null ? parentDir.getName() : "";
        if (parentName.startsWith("values")) return;

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) name = name.substring(0, dot);

        if (!name.startsWith(prefix)) {
            String message = "Resource name `" + name + "` does not start with the project's resource prefix `" + prefix + "`";
            context.report(ISSUE, context.getLocation(file), message);
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (prefix == null || prefix.isEmpty()) return;

        File file = context.file;
        if (file == null) return;

        File parentDir = file.getParentFile();
        String parentName = parentDir != null ? parentDir.getName() : "";
        if (!parentName.startsWith("values")) return;

        if (element.hasAttribute("name")) {
            String name = element.getAttribute("name");
            if (!name.isEmpty() && !name.startsWith(prefix)) {
                String message = "Resource name `" + name + "` does not start with the project's resource prefix `" + prefix + "`";
                context.report(ISSUE, context.getLocation(element.getAttributeNode("name")), message);
            }
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (prefix == null || prefix.isEmpty()) return;

        String name = context.file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) name = name.substring(0, dot);

        if (!name.startsWith(prefix)) {
            String message = "Resource name `" + name + "` does not start with the project's resource prefix `" + prefix + "`";
            context.report(ISSUE, context.getLocation(context.file), message);
        }
    }
}