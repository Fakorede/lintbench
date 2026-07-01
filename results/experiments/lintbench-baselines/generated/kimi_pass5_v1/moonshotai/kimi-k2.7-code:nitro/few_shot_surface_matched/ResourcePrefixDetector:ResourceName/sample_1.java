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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource name does not start with configured resource prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in "
                            + "the project must conform to. This makes it easier to ensure that you "
                            + "do not accidentally combine resources from different libraries, since "
                            + "they all end up in the same shared app namespace. This check flags "
                            + "resource names that do not start with the configured prefix.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(ResourcePrefixDetector.class, Scope.ALL_RESOURCE_FILES));

    private String mResourcePrefix;
    private boolean mEnabled;

    @Override
    public void beforeCheckEachProject(Context context) {
        super.beforeCheckEachProject(context);
        mResourcePrefix = context.getProject().getResourcePrefix();
        mEnabled = mResourcePrefix != null && !mResourcePrefix.isEmpty();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
        mEnabled = false;
        mResourcePrefix = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(com.android.SdkConstants.TAG_RESOURCES);
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType != com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (!mEnabled) {
            return;
        }

        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }

        java.io.File parent = context.file.getParentFile();
        if (parent != null && parent.getName().startsWith("values")) {
            return;
        }

        String resourceName = stripExtension(fileName);
        checkResourceName(resourceName, context, context.getLocation());
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (!mEnabled) {
            return;
        }

        String resourceName = stripExtension(context.file.getName());
        checkResourceName(resourceName, context, context.getLocation());
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!mEnabled) {
            return;
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                Attr nameAttr = childElement.getAttributeNode(com.android.SdkConstants.ATTR_NAME);
                if (nameAttr != null) {
                    String name = nameAttr.getValue();
                    if (!name.startsWith(mResourcePrefix)) {
                        checkResourceName(name, context, context.getLocation(nameAttr));
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    private void checkResourceName(String name, Context context, Location location) {
        if (name.startsWith(mResourcePrefix)) {
            return;
        }

        String message =
                "Resource name \""
                        + name
                        + "\" does not start with the project's resource prefix \""
                        + mResourcePrefix
                        + "\"";
        context.report(ISSUE, location, message);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}