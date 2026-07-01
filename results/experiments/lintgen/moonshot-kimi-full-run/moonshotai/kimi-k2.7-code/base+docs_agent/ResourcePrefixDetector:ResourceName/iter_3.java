package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.DOT_9PNG;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector
        implements Detector.XmlScanner, Detector.ResourceFolderScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. "
                            + "This makes it easier to ensure that you don't accidentally combine resources from different libraries, since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.RESOURCE_FOLDER)));

    private String mResourcePrefix;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mResourcePrefix =
                context.getProject().getBuildModule() != null
                        ? context.getProject().getBuildModule().getResourcePrefix()
                        : null;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mResourcePrefix == null || mResourcePrefix.isEmpty()) {
            return;
        }

        Attr attr = element.getAttributeNode(ATTR_NAME);
        if (attr == null) {
            return;
        }

        String name = attr.getValue();
        if (name.isEmpty()) {
            return;
        }

        if (!libraryPrefixMatches(mResourcePrefix, name)) {
            context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    String.format(
                            "Resource name `%1$s` does not start with the configured resource prefix `%2$s`",
                            name,
                            mResourcePrefix));
        }
    }

    @Override
    public void checkFolder(
            @NonNull ResourceContext context,
            @NonNull ResourceFolderType folderType,
            @NonNull File folder) {
        if (mResourcePrefix == null || mResourcePrefix.isEmpty()) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            return;
        }

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

            String resourceName = getResourceName(fileName);
            if (resourceName.isEmpty()) {
                continue;
            }

            if (!libraryPrefixMatches(mResourcePrefix, resourceName)) {
                context.report(
                        ISSUE,
                        Location.create(file),
                        String.format(
                                "Resource name `%1$s` does not start with the configured resource prefix `%2$s`",
                                resourceName,
                                mResourcePrefix));
            }
        }
    }

    private static String getResourceName(@NonNull String fileName) {
        if (fileName.endsWith(DOT_9PNG)) {
            return fileName.substring(0, fileName.length() - DOT_9PNG.length());
        }

        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static boolean libraryStyleMatches(@NonNull String prefix, @NonNull String name) {
        return name.startsWith(prefix)
                && (name.length() == prefix.length()
                        || name.charAt(prefix.length()) == '_');
    }

    public static boolean libraryPrefixMatches(@NonNull String prefix, @NonNull String name) {
        return libraryStyleMatches(prefix, name)
                || libraryStyleMatches(underlinesToCamelCase(prefix), name)
                || libraryStyleMatches(camelCaseToUnderlines(prefix), name);
    }

    private static String underlinesToCamelCase(@NonNull String string) {
        StringBuilder sb = new StringBuilder();
        boolean upcase = false;
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == '_') {
                upcase = true;
            } else if (upcase) {
                sb.append(Character.toUpperCase(c));
                upcase = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String camelCaseToUnderlines(@NonNull String string) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}