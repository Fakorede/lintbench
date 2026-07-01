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
import java.util.EnumSet;
import java.util.List;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. "
                    + "This makes it easier to ensure that you don't accidentally combine resources from different libraries, "
                    + "since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    ResourcePrefixDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.BINARY_RESOURCE_FILE))
    );

    private List<String> mPrefixes;
    private boolean mCheckValues;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("resources");
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        List<String> prefixes = context.getProject().getResourcePrefixes();
        mPrefixes = prefixes != null ? prefixes : Collections.emptyList();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        mPrefixes = null;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mCheckValues = context.getResourceFolderType() == com.android.resources.ResourceFolderType.VALUES;
        if (!mCheckValues && mPrefixes != null && !mPrefixes.isEmpty()) {
            String name = getResourceName(context.file.getName());
            if (name != null && !name.isEmpty()) {
                checkName(context, name, Location.create(context.file));
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (!mCheckValues || mPrefixes == null || mPrefixes.isEmpty()) {
            return;
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            org.w3c.dom.Element childElement = (org.w3c.dom.Element) child;

            org.w3c.dom.Attr nameAttr = childElement.getAttributeNode("name");
            if (nameAttr != null) {
                checkName(context, nameAttr.getValue(), context.getValueLocation(nameAttr));
            }

            if ("declare-styleable".equals(childElement.getTagName())) {
                org.w3c.dom.NodeList attrs = childElement.getChildNodes();
                for (int j = 0, m = attrs.getLength(); j < m; j++) {
                    org.w3c.dom.Node attrNode = attrs.item(j);
                    if (attrNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                        continue;
                    }
                    org.w3c.dom.Element attrElement = (org.w3c.dom.Element) attrNode;

                    org.w3c.dom.Attr attrName = attrElement.getAttributeNode("name");
                    if (attrName != null) {
                        checkName(context, attrName.getValue(), context.getValueLocation(attrName));
                    }
                }
            }
        }
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (mPrefixes == null || mPrefixes.isEmpty()) {
            return;
        }
        String name = getResourceName(context.file.getName());
        if (name != null && !name.isEmpty()) {
            checkName(context, name, Location.create(context.file));
        }
    }

    private void checkName(Context context, String name, Location location) {
        if (name == null || name.isEmpty()) {
            return;
        }
        for (String prefix : mPrefixes) {
            if (name.startsWith(prefix)) {
                return;
            }
        }

        String message;
        if (mPrefixes.size() == 1) {
            message = "Resource name '" + name + "' does not start with the required prefix '"
                    + mPrefixes.get(0) + "'";
        } else {
            message = "Resource name '" + name + "' does not start with any of the required prefixes "
                    + mPrefixes;
        }
        context.report(ISSUE, location, message);
    }

    private static String getResourceName(String fileName) {
        int dot = fileName.indexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}