package com.android.tools.lint.checks;

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
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements Detector.XmlScanner, Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    ResourcePrefixDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. This makes it easier to ensure that you don't accidentally combine resources from different libraries, since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final String NAME_ATTRIBUTE = "name";
    private static final String ANDROID_NS_PREFIX = "android:";

    private String mPrefix;

    @Override
    public Collection<String> getApplicableElements() {
        return Detector.XmlScanner.ALL;
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        mPrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(Context context) {
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        File file = context.getFile();
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String folderType = getFolderType(parent.getName());
        if ("values".equals(folderType)) {
            return;
        }

        String fileName = file.getName();
        int dot = fileName.indexOf('.');
        if (dot != -1) {
            fileName = fileName.substring(0, dot);
        }

        if (!fileName.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named '%s' does not start with the project's resource prefix '%s'",
                            fileName, mPrefix);
            context.report(ISSUE, Location.create(file), message);
        }
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        File file = context.getFile();
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        if (!"values".equals(getFolderType(parent.getName()))) {
            return;
        }

        if (!element.hasAttribute(NAME_ATTRIBUTE)) {
            return;
        }

        String name = element.getAttribute(NAME_ATTRIBUTE);
        if (name.isEmpty() || name.startsWith(ANDROID_NS_PREFIX)) {
            return;
        }

        if (!name.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named '%s' does not start with the project's resource prefix '%s'",
                            name, mPrefix);
            context.report(ISSUE, context.getNameLocation(element), message);
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        String name = context.getResourceName();
        if (name == null || name.isEmpty()) {
            return;
        }

        if (!name.startsWith(mPrefix)) {
            String message =
                    String.format(
                            "Resource named '%s' does not start with the project's resource prefix '%s'",
                            name, mPrefix);
            context.report(ISSUE, context.getLocation(), message);
        }
    }

    private static String getFolderType(String folderName) {
        int dash = folderName.indexOf('-');
        return dash == -1 ? folderName : folderName.substring(0, dash);
    }
}