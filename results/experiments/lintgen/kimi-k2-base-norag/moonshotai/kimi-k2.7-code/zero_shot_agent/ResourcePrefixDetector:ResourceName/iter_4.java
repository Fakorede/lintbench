package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;

public class ResourcePrefixDetector extends ResourceXmlDetector {
    private static final String TAG_RESOURCES = "resources";
    private static final String TAG_DECLARE_STYLEABLE = "declare-styleable";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_PARENT = "parent";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_PREFIX = "prefix";

    @Nullable
    private String mPrefix;

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "...",
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
        mPrefix = getResourcePrefix(context);
        if (mPrefix == null) {
            return;
        }
        ResourceFolderType folderType = ResourceFolderType.getFolderType(context.file.getParentFile().getName());
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String name = dot == -1 ? fileName : fileName.substring(0, dot);
        if (!name.startsWith(mPrefix)) {
            context.report(ISSUE, Location.create(context.file),
                    String.format("Resource name '%1$s' should start with prefix '%2$s'", name, mPrefix));
        }
    }

    @Nullable
    private String getResourcePrefix(Context context) { ... }

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
            if (TAG_RESOURCES.equals(parentTag) || TAG_DECLARE_STYLEABLE.equals(parentTag)) {
                if (!name.startsWith(mPrefix)) {
                    context.report(ISSUE, context.getLocation(element),
                            String.format("Resource name '%1$s' should start with prefix '%2$s'", name, mPrefix));
                }
            }
        }
    }
}