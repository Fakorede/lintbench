package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResourcePrefixDetector extends Detector implements Detector.XmlScanner, Detector.BinaryResourceScanner {

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
            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)
        )
    );

    private String getResourcePrefix(com.android.tools.lint.detector.api.Project project) {
        if (project == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = project.getClass().getMethod("getResourcePrefix");
            return (String) method.invoke(project);
        } catch (Throwable t) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = project.getClass().getMethod("getGradleProjectModel");
            Object model = method.invoke(project);
            if (model != null) {
                java.lang.reflect.Method prefixMethod = model.getClass().getMethod("getResourcePrefix");
                return (String) prefixMethod.invoke(model);
            }
        } catch (Throwable t) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = project.getClass().getMethod("getGradleModel");
            Object model = method.invoke(project);
            if (model != null) {
                java.lang.reflect.Method prefixMethod = model.getClass().getMethod("getResourcePrefix");
                return (String) prefixMethod.invoke(model);
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = getResourcePrefix(context.getProject());
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }
        if (folderType != ResourceFolderType.VALUES) {
            String fileName = context.getFile().getName();
            int dot = fileName.indexOf('.');
            String name = dot != -1 ? fileName.substring(0, dot) : fileName;
            if (!name.startsWith(prefix)) {
                context.report(
                    ISSUE,
                    Location.create(context.getFile()),
                    "Resource named '" + name + "' does not start with the project's resource prefix '" + prefix + "'"
                );
            }
        } else {
            Element root = document.getDocumentElement();
            if (root != null && "resources".equals(root.getTagName())) {
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) child;
                        String name = element.getAttribute("name");
                        if (name != null && !name.isEmpty()) {
                            if (!name.startsWith(prefix)) {
                                Attr nameAttr = element.getAttributeNode("name");
                                Location location = nameAttr != null ? context.getLocation(nameAttr) : context.getLocation(element);
                                context.report(
                                    ISSUE,
                                    location,
                                    "Resource '" + name + "' does not start with the project's resource prefix '" + prefix + "'"
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        String prefix = getResourcePrefix(context.getProject());
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            String fileName = context.getFile().getName();
            int dot = fileName.indexOf('.');
            String name = dot != -1 ? fileName.substring(0, dot) : fileName;
            if (!name.startsWith(prefix)) {
                context.report(
                    ISSUE,
                    Location.create(context.getFile()),
                    "Resource named '" + name + "' does not start with the project's resource prefix '" + prefix + "'"
                );
            }
        }
    }
}