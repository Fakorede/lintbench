package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.BinaryResourceScanner;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources "
                    + "in the project must conform to. This makes it easier to ensure that you don't "
                    + "accidentally combine resources from different libraries, since they all end "
                    + "up in the same shared app namespace.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    ResourcePrefixDetector.class,
                    java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)
            )
    );

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                "string", "dimen", "color", "style", "declare-styleable", "item", "integer",
                "array", "string-array", "integer-array", "plurals", "attr"
        );
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        // No-op, but overridden as required by specification
    }

    @Override
    public void afterCheckEachProject(Context context) {
        // No-op, but overridden as required by specification
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            String prefix = xmlContext.getProject().getResourcePrefix();
            if (prefix == null || prefix.isEmpty()) {
                return;
            }

            com.android.resources.ResourceFolderType folderType = xmlContext.getFolderType();
            if (folderType != null && folderType != com.android.resources.ResourceFolderType.VALUES) {
                String fileName = xmlContext.file.getName();
                int dot = fileName.indexOf('.');
                String resourceName = dot != -1 ? fileName.substring(0, dot) : fileName;
                if (!resourceName.startsWith(prefix)) {
                    xmlContext.report(
                            ISSUE,
                            Location.create(xmlContext.file),
                            "The resource name `" + resourceName + "` must be prefixed with `" + prefix + "`"
                    );
                }
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        if (element.hasAttribute(com.android.SdkConstants.ATTR_NAME)) {
            String name = element.getAttribute(com.android.SdkConstants.ATTR_NAME);
            if (name.isEmpty()) {
                return;
            }

            boolean isStyle = "style".equals(element.getTagName());
            boolean matches = false;
            if (name.startsWith(prefix)) {
                matches = true;
            } else if (isStyle) {
                String camel = snakeToCamel(prefix);
                if (name.startsWith(camel)) {
                    matches = true;
                }
            }

            if (!matches) {
                org.w3c.dom.Attr attribute = element.getAttributeNode(com.android.SdkConstants.ATTR_NAME);
                context.report(
                        ISSUE,
                        element,
                        context.getValueLocation(attribute),
                        "The resource name `" + name + "` must be prefixed with `" + prefix + "`"
                );
            }
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.indexOf('.');
        String resourceName = dot != -1 ? fileName.substring(0, dot) : fileName;
        if (!resourceName.startsWith(prefix)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    "The resource name `" + resourceName + "` must be prefixed with `" + prefix + "`"
            );
        }
    }

    private static String snakeToCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
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
}