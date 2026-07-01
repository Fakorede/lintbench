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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector implements Detector.XmlScanner, Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. " +
                    "This makes it easier to ensure that you don't accidentally combine resources from different libraries, " +
                    "since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private String mPrefix;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mPrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }
        if (context instanceof XmlContext) {
            java.io.File parentFile = context.file.getParentFile();
            String parent = parentFile != null ? parentFile.getName() : "";
            // In values folders, resource names are defined by the 'name' attribute, handled in visitElement.
            if (parent.startsWith("values")) {
                return;
            }
            String fileName = context.file.getName();
            int dot = fileName.lastIndexOf('.');
            String resourceName = dot > 0 ? fileName.substring(0, dot) : fileName;
            if (!resourceName.startsWith(mPrefix)) {
                context.report(ISSUE, Location.create(context.file),
                        "Resource name does not start with prefix `" + mPrefix + "`");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }
        String name = element.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            if (!name.startsWith(mPrefix)) {
                context.report(ISSUE, context.getLocation(element.getAttributeNode("name")),
                        "Resource name does not start with prefix `" + mPrefix + "`");
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String resourceName = dot > 0 ? fileName.substring(0, dot) : fileName;
        if (!resourceName.startsWith(mPrefix)) {
            context.report(ISSUE, Location.create(context.file),
                    "Resource name does not start with prefix `" + mPrefix + "`");
        }
    }
}