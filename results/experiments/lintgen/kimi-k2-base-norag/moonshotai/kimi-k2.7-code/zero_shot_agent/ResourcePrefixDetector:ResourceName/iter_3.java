package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;

public class ResourcePrefixDetector extends ResourceXmlDetector {
    private static final String TAG_RESOURCES = "resources";
    private static final String TAG_DECLARE_STYLEABLE = "declare-styleable";
    private static final String ATTR_NAME = "name";

    @Nullable
    private String mPrefix;

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. "
                    + "This makes it easier to ensure that you don't accidentally combine resources from different libraries, "
                    + "since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPrefix = context.getProject().getResourcePrefix();
        if (mPrefix == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(context.file.getParentFile());
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String name = dot == -1 ? fileName : fileName.substring(0, dot);
        if (!name.startsWith(mPrefix)) {
            context.report(ISSUE, Location.create(context.file), context.getValueString(name),
                    "Resource name '" + name + "' should start with prefix '" + mPrefix + "'");
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty() || name.indexOf(':') != -1) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (TAG_RESOURCES.equals(parentTag) ||
                    TAG_DECLARE_STYLEABLE.equals(parentTag)) {
                if (!name.startsWith(mPrefix)) {
                    context.report(ISSUE, context.getLocation(element),
                            "Resource name '" + name + "' should start with prefix '" + mPrefix + "'");
                }
            }
        }
    }
}