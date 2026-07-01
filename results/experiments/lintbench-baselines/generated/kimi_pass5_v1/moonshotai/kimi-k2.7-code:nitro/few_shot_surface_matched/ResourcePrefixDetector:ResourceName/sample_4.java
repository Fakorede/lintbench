package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the "
                            + "project must conform to. This makes it easier to ensure that you "
                            + "don't accidentally combine resources from different libraries, "
                            + "since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)));

    private String mPrefix;

    @Override
    public void beforeCheckEachProject(Context context) {
        mPrefix = getPrefix(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        mPrefix = null;
    }

    private String getPrefix(Context context) {
        Project project = context.getProject();
        if (project == null) {
            return null;
        }
        String prefix = project.getResourcePrefix();
        return (prefix == null || prefix.isEmpty()) ? null : prefix;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "resources",
                "string",
                "drawable",
                "color",
                "dimen",
                "style",
                "array",
                "string-array",
                "integer-array",
                "plurals",
                "integer",
                "bool",
                "item",
                "attr",
                "declare-styleable",
                "fraction",
                "id");
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (mPrefix == null || !(context instanceof XmlContext)) {
            return;
        }
        java.io.File parent = context.file.getParentFile();
        if (parent == null) {
            return;
        }
        String folder = parent.getName();
        if (folder.startsWith("values")) {
            return;
        }
        String baseName = getResourceBaseName(context.file.getName());
        if (!baseName.startsWith(mPrefix)) {
            context.report(
                    ISSUE,
                    context.getLocation(),
                    "Resource name should start with prefix '" + mPrefix + "'");
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mPrefix == null) {
            return;
        }
        if (!"resources".equals(element.getParentNode().getNodeName())) {
            return;
        }
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }
        String name = nameAttr.getValue();
        if (name.isEmpty() || name.startsWith(mPrefix)) {
            return;
        }
        context.report(
                ISSUE,
                nameAttr,
                context.getLocation(nameAttr),
                "Resource name should start with prefix '" + mPrefix + "'");
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (mPrefix == null) {
            return;
        }
        String name = context.getName();
        if (name == null) {
            name = getResourceBaseName(context.file.getName());
        }
        if (!name.startsWith(mPrefix)) {
            context.report(
                    ISSUE,
                    context.getLocation(),
                    "Resource name should start with prefix '" + mPrefix + "'");
        }
    }

    private static String getResourceBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}