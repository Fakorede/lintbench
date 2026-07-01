package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.model.LintModelModule;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResourcePrefixDetector extends Detector
        implements XmlScanner, ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. "
                    + "This makes it easier to ensure that you don't accidentally combine resources from different libraries, "
                    + "since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    ResourcePrefixDetector.class,
                    Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_RESOURCES);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String prefix = getResourcePrefix(context);
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                Attr nameAttr = childElement.getAttributeNode(ATTR_NAME);
                if (nameAttr != null) {
                    String name = nameAttr.getValue();
                    if (!name.startsWith(prefix)) {
                        context.report(
                                ISSUE,
                                nameAttr,
                                context.getLocation(nameAttr),
                                String.format(
                                        "Resource named '%1$s' does not start with the expected prefix '%2$s'",
                                        name, prefix));
                    }
                }
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(ResourceContext context, String folderName) {
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType == ResourceFolderType.VALUES) {
            return;
        }

        String prefix = getResourcePrefix(context);
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        File folder = context.getFile();
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && !file.isHidden()) {
                String baseName = getBaseName(file.getName());
                if (!baseName.startsWith(prefix)) {
                    context.report(
                            ISSUE,
                            Location.create(file),
                            String.format(
                                    "Resource named '%1$s' does not start with the expected prefix '%2$s'",
                                    baseName, prefix));
                }
            }
        }
    }

    private static String getResourcePrefix(Context context) {
        Project project = context.getProject();
        if (project == null) {
            return null;
        }
        LintModelModule module = project.getBuildModule();
        if (module == null) {
            return null;
        }
        return module.getResourcePrefix();
    }

    private static String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}