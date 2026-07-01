package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.Collection;
import java.util.Collections;

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
    public void beforeCheckFile(Context context) {
        File file = context.file;
        File parentFile = file.getParentFile();
        if (parentFile != null) {
            String parentName = parentFile.getName();
            ResourceFolderType folderType = ResourceFolderType.getFolderType(parentName);
            if (folderType != null && folderType != ResourceFolderType.VALUES) {
                String prefix = context.getProject().getResourcePrefix();
                if (prefix != null && !prefix.isEmpty()) {
                    String fileName = file.getName();
                    String resourceName = fileName;
                    int dot = fileName.indexOf('.');
                    if (dot != -1) {
                        resourceName = fileName.substring(0, dot);
                    }
                    if (!resourceName.startsWith(prefix)) {
                        context.report(
                                ISSUE,
                                Location.create(file),
                                "The resource name '" + resourceName + "' must begin with the prefix '" + prefix + "'"
                        );
                    }
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("resources");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String name = childElement.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    String tagName = childElement.getTagName();
                    boolean isStyle = "style".equals(tagName);
                    if (!isValidValueResourceName(name, prefix, isStyle)) {
                        String message;
                        if (isStyle) {
                            message = "Style named '" + name + "' does not start with the project's resource prefix '" + prefix + "' (or camel-case '" + toCamelCase(prefix) + "')";
                        } else {
                            message = "Resource named '" + name + "' does not start with the project's resource prefix '" + prefix + "'";
                        }
                        context.report(
                                ISSUE,
                                childElement,
                                context.getNameLocation(childElement),
                                message
                        );
                    }
                }
            }
        }
    }

    private boolean isValidValueResourceName(String name, String prefix, boolean isStyle) {
        if (name.startsWith(prefix)) {
            return true;
        }
        if (isStyle) {
            String camelPrefix = toCamelCase(prefix);
            if (name.startsWith(camelPrefix)) {
                return true;
            }
            int lastDot = name.lastIndexOf('.');
            if (lastDot != -1 && lastDot < name.length() - 1) {
                String suffix = name.substring(lastDot + 1);
                if (suffix.startsWith(prefix) || suffix.startsWith(camelPrefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String toCamelCase(String prefix) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else if (Character.isLetterOrDigit(c)) {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}