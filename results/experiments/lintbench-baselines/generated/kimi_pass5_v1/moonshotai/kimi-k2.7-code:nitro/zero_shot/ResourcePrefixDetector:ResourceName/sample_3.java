package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;

public class ResourcePrefixDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            new Implementation(
                    ResourcePrefixDetector.class,
                    Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    private String mPrefix;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        super.beforeCheckProject(context);
        mPrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        if (TAG_RESOURCES.equals(element.getTagName())) {
            return;
        }

        if (!element.hasAttribute(ATTR_NAME)) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        if (!name.startsWith(mPrefix)) {
            context.report(
                    ISSUE,
                    element.getAttributeNode(ATTR_NAME),
                    context.getLocation(element.getAttributeNode(ATTR_NAME)),
                    String.format("Resource name should start with prefix '%1$s'", mPrefix));
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            return;
        }

        String fileName = context.file.getName();
        int dotIndex = fileName.indexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        if (!resourceName.startsWith(mPrefix)) {
            context.report(
                    ISSUE,
                    document.getDocumentElement(),
                    context.getLocation(document.getDocumentElement()),
                    String.format("Resource name '%1$s' should start with prefix '%2$s'",
                            resourceName, mPrefix));
        }
    }
}