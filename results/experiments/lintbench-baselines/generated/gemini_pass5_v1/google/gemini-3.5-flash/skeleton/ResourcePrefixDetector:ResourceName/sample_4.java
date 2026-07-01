package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Location;
import java.io.File;
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
        return java.util.Arrays.asList(
                "string", "color", "dimen", "style", "declare-styleable",
                "array", "string-array", "integer-array", "plurals",
                "bool", "integer", "fraction", "drawable", "item", "attr", "public"
        );
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
        checkResourceFile(context);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = getPrefix(context);
        if (prefix == null) {
            return;
        }

        File file = context.file;
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        String parentName = parent.getName();
        int dash = parentName.indexOf('-');
        String type = dash == -1 ? parentName : parentName.substring(0, dash);
        if (!"values".equals(type)) {
            return;
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            if (name.contains(":")) {
                return;
            }

            if ("item".equals(element.getTagName())) {
                org.w3c.dom.Node parentNode = element.getParentNode();
                if (parentNode instanceof Element) {
                    String parentTagName = ((Element) parentNode).getTagName();
                    if ("style".equals(parentTagName)) {
                        return;
                    }
                }
            }

            if (!name.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The resource name `" + name + "` must begin with the prefix `" + prefix + "`"
                );
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        checkResourceFile(context);
    }

    private void checkResourceFile(@NonNull Context context) {
        String prefix = getPrefix(context);
        if (prefix == null) {
            return;
        }
        File file = context.file;
        File parent = file.getParentFile();
        if (parent != null) {
            String parentName = parent.getName();
            int dash = parentName.indexOf('-');
            String type = dash == -1 ? parentName : parentName.substring(0, dash);
            if (isResourceFolder(type) && !"values".equals(type)) {
                String name = file.getName();
                int dot = name.indexOf('.');
                if (dot != -1) {
                    name = name.substring(0, dot);
                }
                if (!name.startsWith(prefix)) {
                    context.report(
                            ISSUE,
                            Location.create(file),
                            "The resource name `" + name + "` must begin with the prefix `" + prefix + "`"
                    );
                }
            }
        }
    }

    private String getPrefix(Context context) {
        String prefix = context.getProject().getResourcePrefix();
        return (prefix == null || prefix.isEmpty()) ? null : prefix;
    }

    private static boolean isResourceFolder(String folderName) {
        return folderName.equals("anim")
                || folderName.equals("animator")
                || folderName.equals("color")
                || folderName.equals("drawable")
                || folderName.equals("font")
                || folderName.equals("layout")
                || folderName.equals("menu")
                || folderName.equals("mipmap")
                || folderName.equals("navigation")
                || folderName.equals("raw")
                || folderName.equals("transition")
                || folderName.equals("values")
                || folderName.equals("xml");
    }
}