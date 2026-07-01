package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_EAT_COMMENT;
import static com.android.SdkConstants.TAG_ENUM;
import static com.android.SdkConstants.TAG_FLAG;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_PUBLIC;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_SKIP;
import static com.android.SdkConstants.TAG_STYLE;

public class ResourcePrefixDetector extends ResourceXmlDetector implements Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ResourcePrefixDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project "
                    + "must conform to. This makes it easier to ensure that you don't accidentally "
                    + "combine resources from different libraries, since they all end up in the same "
                    + "shared app namespace.",
            Category.USABILITY,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    private Collection<String> mPrefixes = Collections.emptyList();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        Project project = context.getProject();
        if (project.isGradleProject()) {
            Collection<String> prefixes = project.getResourcePrefixes();
            mPrefixes = prefixes != null ? prefixes : Collections.emptyList();
        } else {
            mPrefixes = Collections.emptyList();
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (mPrefixes.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            NodeList elements = document.getElementsByTagName("*");
            for (int i = 0, n = elements.getLength(); i < n; i++) {
                Node node = elements.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element element = (Element) node;
                if (!isValueResourceElement(element)) {
                    continue;
                }

                Attr nameAttr = element.getAttributeNode(ATTR_NAME);
                if (nameAttr == null) {
                    continue;
                }

                String name = nameAttr.getValue();
                if (name.isEmpty() || name.indexOf(':') != -1 || startsWithPrefix(name)) {
                    continue;
                }

                context.report(
                        ISSUE,
                        context.getLocation(nameAttr),
                        String.format(
                                "Resource named '%1$s' does not start with %2$s",
                                name,
                                describePrefixes()));
            }
        } else {
            String baseName = LintUtils.getBaseName(context.file.getName());
            if (!startsWithPrefix(baseName)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        String.format(
                                "Resource file name '%1$s' does not start with %2$s",
                                baseName,
                                describePrefixes()));
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefixes.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        String baseName = LintUtils.getBaseName(context.file.getName());
        if (!startsWithPrefix(baseName)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    String.format(
                            "Resource file name '%1$s' does not start with %2$s",
                            baseName,
                            describePrefixes()));
        }
    }

    private boolean isValueResourceElement(@NonNull Element element) {
        String tagName = element.getTagName();
        if (tagName.equals(TAG_RESOURCES)
                || tagName.equals(TAG_EAT_COMMENT)
                || tagName.equals(TAG_SKIP)
                || tagName.equals(TAG_PUBLIC)
                || tagName.equals(TAG_ENUM)
                || tagName.equals(TAG_FLAG)) {
            return false;
        }

        if (tagName.equals(TAG_ITEM)) {
            Node parent = element.getParentNode();
            if (parent instanceof Element && ((Element) parent).getTagName().equals(TAG_STYLE)) {
                return false;
            }
        }

        return true;
    }

    private boolean startsWithPrefix(@NonNull String name) {
        for (String prefix : mPrefixes) {
            if (!prefix.isEmpty() && name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private String describePrefixes() {
        if (mPrefixes.isEmpty()) {
            return "the configured resource prefix";
        }
        if (mPrefixes.size() == 1) {
            return "the resource prefix '" + mPrefixes.iterator().next() + "'";
        }
        return "one of the resource prefixes '" + String.join("', '", mPrefixes) + "'";
    }
}