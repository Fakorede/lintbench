package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ResourcePrefixDetector extends Detector implements Detector.XmlScanner, Detector.ResourceFolderScanner {

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
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Project project = context.getProject();
        String prefix = project.getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        String tagName = element.getTagName();
        Node parent = element.getParentNode();
        if (parent == null) {
            return;
        }
        String parentTagName = parent.getNodeName();

        boolean isResourceDefinition = "resources".equals(parentTagName)
                || ("declare-styleable".equals(parentTagName) && "attr".equals(tagName));

        if (!isResourceDefinition) {
            return;
        }

        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (name.startsWith("android:")) {
            return;
        }

        if (!matchesPrefix(name, prefix)) {
            context.report(
                    ISSUE,
                    nameAttr,
                    context.getValueLocation(nameAttr),
                    "Resource named '" + name + "' does not start with the project's resource prefix '" + prefix + "'"
            );
        }
    }

    @Override
    public void checkFile(ResourceContext context) {
        Project project = context.getProject();
        String prefix = project.getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.indexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;

        if (!matchesPrefix(baseName, prefix)) {
            context.report(
                    ISSUE,
                    context.getLocation(context.file),
                    "Resource named '" + baseName + "' does not start with the project's resource prefix '" + prefix + "'"
            );
        }
    }

    private static boolean matchesPrefix(String name, String prefix) {
        if (name.startsWith(prefix)) {
            return true;
        }
        String camelCase = toCamelCase(prefix);
        if (!camelCase.isEmpty() && (name.startsWith(camelCase) || name.startsWith(decapitalize(camelCase)))) {
            return true;
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
            } else {
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

    private static String decapitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}