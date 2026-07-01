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
import java.util.Collections;
import java.util.EnumSet;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure that "
                            + "you don't accidentally combine resources from different libraries, "
                            + "since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op
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

        String folderName = context.file.getParentFile().getName();
        if (folderName.startsWith("values")) {
            return;
        }

        String resourceName = fileName.substring(0, fileName.length() - 4);
        if (!resourceName.startsWith(prefix)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    String.format("Resource file name `%s` does not start with project prefix `%s`", fileName, prefix)
            );
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String folderName = context.file.getParentFile().getName();
        if (!folderName.startsWith("values")) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent != null) {
            String parentName = parent.getNodeName();
            if ("resources".equals(parentName) || "declare-styleable".equals(parentName)) {
                String name = element.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    if (!name.startsWith("android:") && !name.startsWith(prefix)) {
                        context.report(
                                ISSUE,
                                context.getNameLocation(element),
                                String.format("Resource name `%s` does not start with project prefix `%s`", name, prefix)
                        );
                    }
                }
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String resourceName = dot != -1 ? fileName.substring(0, dot) : fileName;

        if (!resourceName.startsWith(prefix)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    String.format("Resource file name `%s` does not start with project prefix `%s`", fileName, prefix)
            );
        }
    }
}