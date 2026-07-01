package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE =
            Issue.create(
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
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)));

    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";

    @Nullable
    private String mPrefix;

    public ResourcePrefixDetector() {
    }

    // ---- Implements Detector ----

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        mPrefix = project.getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Nothing to do here, but required by specification
    }

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "declare-styleable",
                "attr",
                "style",
                "dimen",
                "color",
                "string",
                "integer",
                "bool",
                "array",
                "string-array",
                "integer-array",
                "plurals",
                "fraction",
                "item",
                "layout",
                "menu",
                "drawable",
                "anim",
                "animator",
                "interpolator",
                "transition",
                "raw",
                "xml",
                "font",
                "navigation"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        String tagName = element.getTagName();

        // For value resources, the name attribute holds the resource name
        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            // Try android:name
            NamedNodeMap attrs = element.getAttributes();
            if (attrs != null) {
                for (int i = 0; i < attrs.getLength(); i++) {
                    Attr attr = (Attr) attrs.item(i);
                    if (ATTR_NAME.equals(attr.getLocalName())) {
                        nameAttr = attr;
                        break;
                    }
                }
            }
        }

        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Skip items that define a type other than the expected resource types
        // (e.g. <item type="id"> should also be checked)
        if ("item".equals(tagName)) {
            Attr typeAttr = element.getAttributeNode(ATTR_TYPE);
            if (typeAttr == null) {
                return;
            }
        }

        // Strip style parent references (e.g. "ParentStyle.MyStyle" -> check "MyStyle")
        // Actually for resources, the full name must match the prefix
        if (!name.startsWith(mPrefix)) {
            String message = String.format(
                    "Resource named '`%1$s`' does not start with the project's resource prefix "
                            + "'`%2$s`'; rename to '`%3$s`'",
                    name, mPrefix, mPrefix + name);
            context.report(ISSUE, nameAttr, context.getValueLocation(nameAttr), message);
        }
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return false;
        }
        // Apply to all binary resource folder types (drawables, layouts, etc.)
        return folderType != ResourceFolderType.VALUES;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        String fileName = context.file.getName();
        // Strip the extension to get the resource name
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        if (resourceName.isEmpty()) {
            return;
        }

        if (!resourceName.startsWith(mPrefix)) {
            String message = String.format(
                    "Resource named '`%1$s`' does not start with the project's resource prefix "
                            + "'`%2$s`'; rename to '`%3$s`'",
                    resourceName, mPrefix, mPrefix + resourceName);
            context.report(ISSUE, context.getLocation(context.file), message);
        }
    }
}