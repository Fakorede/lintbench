package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.jetbrains.annotations.NonNull;

public class ResourcePrefixDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
        "ResourceName",
        "Resource with Wrong Prefix",
        "In Gradle projects you can specify a resource prefix that all resources " +
        "in the project must conform to. This makes it easier to ensure that you don't " +
        "accidentally combine resources from different libraries, since they all end " +
        "up in the same shared app namespace.",
        Category.CORRECTNESS,
        8,
        Severity.ERROR,
        new Implementation(
            ResourcePrefixDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        Project project = context.getProject();
        String prefix = project.getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        File file = context.file;
        String parentName = file.getParentFile().getName();
        ResourceFolderType folderType = ResourceFolderType.getFolderType(parentName);
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            String fileName = file.getName();
            int dot = fileName.indexOf('.');
            String baseName = dot != -1 ? fileName.substring(0, dot) : fileName;
            if (!baseName.startsWith(prefix)) {
                context.report(
                    ISSUE,
                    Location.create(file),
                    "Resource named '" + baseName + "' does not start with the project's resource prefix '" + prefix + "'"
                );
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Project project = context.getProject();
        String prefix = project.getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        File file = context.file;
        String parentName = file.getParentFile().getName();
        ResourceFolderType folderType = ResourceFolderType.getFolderType(parentName);
        if (folderType == null) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            Node parent = element.getParentNode();
            if (parent != null && SdkConstants.TAG_RESOURCES.equals(parent.getNodeName())) {
                Attr nameAttr = element.getAttributeNode(SdkConstants.ATTR_NAME);
                if (nameAttr != null) {
                    String name = nameAttr.getValue();
                    if (!name.startsWith(prefix)) {
                        context.report(
                            ISSUE,
                            nameAttr,
                            context.getLocation(nameAttr),
                            "Resource named '" + name + "' does not start with the project's resource prefix '" + prefix + "'"
                        );
                    }
                }
            }
        } else {
            NamedNodeMap attributes = element.getAttributes();
            if (attributes != null) {
                for (int i = 0; i < attributes.getLength(); i++) {
                    Attr attr = (Attr) attributes.item(i);
                    String value = attr.getValue();
                    if (value.startsWith(SdkConstants.NEW_ID_PREFIX)) {
                        String id = value.substring(SdkConstants.NEW_ID_PREFIX.length());
                        if (!id.startsWith(prefix)) {
                            context.report(
                                ISSUE,
                                attr,
                                context.getValueLocation(attr),
                                "Resource named '" + id + "' does not start with the project's resource prefix '" + prefix + "'"
                            );
                        }
                    }
                }
            }
        }
    }
}