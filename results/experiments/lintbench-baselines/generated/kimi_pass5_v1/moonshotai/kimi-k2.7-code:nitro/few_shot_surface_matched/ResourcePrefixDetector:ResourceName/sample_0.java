package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the "
                            + "project must conform to. This makes it easier to ensure that you don't "
                            + "accidentally combine resources from different libraries, since they all "
                            + "end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.BINARY_RESOURCE_FILE_SCOPE)));

    private static final List<String> RESOURCE_TAGS =
            Arrays.asList(
                    SdkConstants.TAG_STRING,
                    SdkConstants.TAG_STRING_ARRAY,
                    SdkConstants.TAG_INTEGER,
                    SdkConstants.TAG_INTEGER_ARRAY,
                    SdkConstants.TAG_ARRAY,
                    SdkConstants.TAG_BOOL,
                    SdkConstants.TAG_COLOR,
                    SdkConstants.TAG_DIMEN,
                    SdkConstants.TAG_DRAWABLE,
                    SdkConstants.TAG_STYLE,
                    SdkConstants.TAG_DECLARE_STYLEABLE,
                    SdkConstants.TAG_ATTR,
                    SdkConstants.TAG_PLURALS,
                    SdkConstants.TAG_ITEM);

    private String mPrefix;
    private boolean mCheck;
    private ResourceFolderType mFolderType;

    @Override
    public Collection<String> getApplicableElements() {
        return RESOURCE_TAGS;
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        mPrefix = context.getProject().getResourcePrefix();
        mCheck = mPrefix != null && !mPrefix.isEmpty();
    }

    @Override
    public void afterCheckEachProject(Context context) {
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mFolderType =
                ResourceFolderType.getFolderType(context.file.getParentFile().getName());

        if (!mCheck || mFolderType == null || mFolderType == ResourceFolderType.VALUES) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.indexOf('.');
        String resourceName = dot >= 0 ? fileName.substring(0, dot) : fileName;

        if (!resourceName.startsWith(mPrefix)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    "Resource file name should start with prefix '" + mPrefix + "'");
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!mCheck || mFolderType != ResourceFolderType.VALUES) {
            return;
        }

        if (SdkConstants.TAG_ITEM.equals(element.getTagName())
                && !element.hasAttribute(SdkConstants.ATTR_TYPE)) {
            return;
        }

        Attr nameNode =
                element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (nameNode == null) {
            return;
        }

        String name = nameNode.getValue();
        if (!name.startsWith(mPrefix)) {
            context.report(
                    ISSUE,
                    nameNode,
                    context.getLocation(nameNode),
                    "Resource name '" + name + "' should start with prefix '" + mPrefix + "'");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType != ResourceFolderType.VALUES;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (!mCheck) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.indexOf('.');
        String resourceName = dot >= 0 ? fileName.substring(0, dot) : fileName;

        if (!resourceName.startsWith(mPrefix)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    "Resource file name should start with prefix '" + mPrefix + "'");
        }
    }
}