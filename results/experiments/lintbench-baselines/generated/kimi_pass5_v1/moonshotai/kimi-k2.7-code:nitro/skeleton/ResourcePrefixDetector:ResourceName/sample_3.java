package com.android.tools.lint.checks;

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
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    ResourcePrefixDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the "
                            + "project must conform to. This makes it easier to ensure that you do "
                            + "not accidentally combine resources from different libraries, since "
                            + "they all end up in the same shared app namespace. This resource does "
                            + "not start with the project's configured resource prefix.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private String mPrefix;

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        mPrefix = context.getProject().getResourcePrefix();
        if (mPrefix != null && mPrefix.isEmpty()) {
            mPrefix = null;
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        mPrefix = null;
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (mPrefix == null || !(context instanceof XmlContext)) {
            return;
        }
        File file = context.getFile();
        String folder = getResourceFolderName(file);
        if ("values".equals(folder)) {
            return;
        }
        checkResourceName(context, file);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mPrefix == null) {
            return;
        }
        File file = context.getFile();
        if (!"values".equals(getResourceFolderName(file))) {
            return;
        }
        checkValueResourceName(context, element);
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (mPrefix == null) {
            return;
        }
        checkResourceName(context, context.getFile());
    }

    private void checkResourceName(Context context, File file) {
        String name = getBaseName(file.getName());
        if (!name.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named `%1$s` should start with the project's resource prefix `%2$s`",
                            name, mPrefix);
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private void checkValueResourceName(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("resources".equals(tag) || "item".equals(tag)) {
            return;
        }
        if (!element.hasAttribute("name")) {
            return;
        }
        String name = element.getAttribute("name");
        if (name.isEmpty() || name.indexOf(':') != -1) {
            return;
        }
        if (!name.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named `%1$s` should start with the project's resource prefix `%2$s`",
                            name, mPrefix);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static String getResourceFolderName(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return null;
        }
        String folder = parent.getName();
        int dash = folder.indexOf('-');
        return dash == -1 ? folder : folder.substring(0, dash);
    }

    private static String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}