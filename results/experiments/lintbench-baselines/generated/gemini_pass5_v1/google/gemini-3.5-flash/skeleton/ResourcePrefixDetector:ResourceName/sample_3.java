package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. This makes it easier to ensure that you don't accidentally combine resources from different libraries, since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }

        String parentName = context.file.getParentFile().getName();
        boolean isValues = parentName.equals("values") || parentName.startsWith("values-");
        if (!isValues) {
            int dot = fileName.indexOf('.');
            String resourceName = dot != -1 ? fileName.substring(0, dot) : fileName;
            if (!resourceName.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        "Resource name `" + resourceName + "` does not start with the project's resource prefix `" + prefix + "`"
                );
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String parentName = context.file.getParentFile().getName();
        boolean isValues = parentName.equals("values") || parentName.startsWith("values-");
        if (!isValues) {
            return;
        }

        String tag = element.getTagName();
        if ("resources".equals(tag)) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        if (parent == null) {
            return;
        }
        String parentTag = parent.getNodeName();
        boolean isResourceDefinition = "resources".equals(parentTag)
                || ("attr".equals(tag) && "declare-styleable".equals(parentTag) && element.hasAttribute("format"));

        if (!isResourceDefinition) {
            return;
        }

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String checkName = name;
        if (checkName.startsWith("Base.")) {
            checkName = checkName.substring(5);
        } else if (checkName.startsWith("Platform.")) {
            checkName = checkName.substring(9);
        }

        if (!checkName.startsWith(prefix)) {
            context.report(
                    ISSUE,
                    context.getNameLocation(element),
                    "Resource name `" + name + "` does not start with the project's resource prefix `" + prefix + "`"
            );
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String fileName = context.file.getName();
        String parentName = context.file.getParentFile().getName();
        boolean isValues = parentName.equals("values") || parentName.startsWith("values-");
        if (!isValues) {
            int dot = fileName.indexOf('.');
            String resourceName = dot != -1 ? fileName.substring(0, dot) : fileName;
            if (!resourceName.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        "Resource name `" + resourceName + "` does not start with the project's resource prefix `" + prefix + "`"
                );
            }
        }
    }
}