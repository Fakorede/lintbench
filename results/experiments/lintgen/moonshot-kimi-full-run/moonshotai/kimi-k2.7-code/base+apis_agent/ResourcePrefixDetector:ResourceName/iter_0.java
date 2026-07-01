package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderContext;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector implements XmlScanner, ResourceFolderScanner {

    private static final String ATTR_NAME = "name";

    private static final String[] VALUE_RESOURCE_TAGS = new String[] {
            "array",
            "string-array",
            "integer-array",
            "attr",
            "bool",
            "color",
            "declare-styleable",
            "dimen",
            "drawable",
            "fraction",
            "integer",
            "item",
            "plurals",
            "string",
            "style"
    };

    private static final Implementation IMPLEMENTATION = new Implementation(
            ResourcePrefixDetector.class,
            EnumSet.of(Scope.RESOURCE_FILE, Scope.RESOURCE_FOLDER));

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project "
                    + "must conform to. This makes it easier to ensure that you don't accidentally "
                    + "combine resources from different libraries, since they all end up in the same "
                    + "shared app namespace. This check looks through all resources in the module and "
                    + "reports any resource names that do not start with the configured prefix. You can "
                    + "configure the prefix with the `resourcePrefix` property in your build.gradle file.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(VALUE_RESOURCE_TAGS);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        String prefix = getResourcePrefix(context);
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        Attr attribute = element.getAttributeNode(ATTR_NAME);
        if (attribute == null) {
            return;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty() || name.indexOf(':') != -1) {
            return;
        }

        if (!name.startsWith(prefix)) {
            String message = String.format(
                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`",
                    name, prefix);
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
        }
    }

    @Override
    public void visitResourceFolder(@NonNull ResourceFolderContext context) {
        String prefix = getResourcePrefix(context);
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        if (context.getFolderType() == ResourceFolderType.VALUES) {
            return;
        }

        File folder = context.getFolder();
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }

            String fileName = file.getName();
            if (fileName.startsWith(".")) {
                continue;
            }

            String name = getResourceName(file);
            if (name.isEmpty()) {
                continue;
            }

            if (!name.startsWith(prefix)) {
                String message = String.format(
                        "Resource named `%1$s` does not start with the project's resource prefix `%2$s`",
                        name, prefix);
                context.report(ISSUE, Location.create(file), message);
            }
        }
    }

    @Nullable
    private static String getResourcePrefix(@NonNull Context context) {
        return context.getProject().getResourcePrefix();
    }

    @NonNull
    private static String getResourceName(@NonNull File file) {
        String fileName = file.getName();
        int dot = fileName.indexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}