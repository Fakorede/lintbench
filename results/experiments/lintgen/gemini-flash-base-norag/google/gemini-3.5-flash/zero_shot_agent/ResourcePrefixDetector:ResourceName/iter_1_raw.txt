package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Locale;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResourcePrefixDetector extends ResourceXmlDetector implements com.android.tools.lint.detector.api.Detector.ResourceFolderScanner {

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
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(@NonNull ResourceFolderContext context) {
    }

    @Override
    public void checkFile(@NonNull ResourceContext context) {
        String prefix = getResourcePrefix(context);
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType != ResourceFolderType.VALUES) {
            String fileName = context.file.getName();
            String baseName = getBaseName(fileName);
            if (!hasPrefix(baseName, prefix)) {
                context.report(
                    ISSUE,
                    Location.create(context.file),
                    String.format("Resource file '%s' does not start with the project's resource prefix '%s'", fileName, prefix)
                );
            }
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = getResourcePrefix(context);
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            Element root = document.getDocumentElement();
            if (root != null && "resources".equals(root.getTagName())) {
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) child;
                        String name = element.getAttribute("name");
                        if (name != null && !name.isEmpty()) {
                            if (!hasPrefix(name, prefix)) {
                                context.report(
                                    ISSUE,
                                    element,
                                    context.getNameLocation(element),
                                    String.format("Resource named '%s' does not start with the project's resource prefix '%s'", name, prefix)
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    private String getResourcePrefix(Context context) {
        try {
            java.lang.reflect.Method method = context.getProject().getClass().getMethod("getResourcePrefix");
            String prefix = (String) method.invoke(context.getProject());
            if (prefix != null) {
                return prefix;
            }
        } catch (Throwable t) {
            // ignore
        }
        try {
            Object model = context.getProject().getGradleProjectModel();
            if (model != null) {
                java.lang.reflect.Method method = model.getClass().getMethod("getResourcePrefix");
                return (String) method.invoke(model);
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private static String getBaseName(String fileName) {
        int index = fileName.indexOf('.');
        if (index != -1) {
            return fileName.substring(0, index);
        }
        return fileName;
    }

    private static boolean hasPrefix(String name, String prefix) {
        if (name.startsWith(prefix)) {
            return true;
        }
        if (name.toLowerCase(Locale.US).startsWith(prefix.toLowerCase(Locale.US))) {
            return true;
        }
        if (prefix.endsWith("_") && prefix.length() > 1) {
            String prefixWithoutUnderscore = prefix.substring(0, prefix.length() - 1);
            if (name.startsWith(prefixWithoutUnderscore) && name.length() > prefixWithoutUnderscore.length()) {
                char nextChar = name.charAt(prefixWithoutUnderscore.length());
                if (Character.isUpperCase(nextChar)) {
                    return true;
                }
            }
        }
        return false;
    }
}