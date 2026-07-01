package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends ResourceXmlDetector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with wrong prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. "
                    + "This makes it easier to ensure that you don't accidentally combine resources from different "
                    + "libraries, since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ResourcePrefixDetector.class, Scope.ALL_RESOURCE_FILES_SCOPE)
    );

    private String mPrefix;

    @Override
    public void beforeCheckProject(Context context) {
        mPrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            return;
        }
        String name = getResourceName(context.file);
        if (name != null && !name.startsWith(mPrefix)) {
            context.report(ISSUE, Location.create(context.file), createMessage(name));
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }
        String name = nameAttr.getValue();
        if (name == null || name.isEmpty() || name.startsWith("android:") || name.indexOf(':') != -1) {
            return;
        }
        if (!name.startsWith(mPrefix)) {
            context.report(ISSUE, context.getValueLocation(nameAttr), createMessage(name));
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            return;
        }
        String name = getResourceName(context.file);
        if (name != null && !name.startsWith(mPrefix)) {
            context.report(ISSUE, Location.create(context.file), createMessage(name));
        }
    }

    private String createMessage(String name) {
        return "Resource name `" + name + "` does not start with the project's resource prefix `" + mPrefix + "`";
    }

    private static String getResourceName(File file) {
        String fileName = file.getName();
        int dot = fileName.indexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}