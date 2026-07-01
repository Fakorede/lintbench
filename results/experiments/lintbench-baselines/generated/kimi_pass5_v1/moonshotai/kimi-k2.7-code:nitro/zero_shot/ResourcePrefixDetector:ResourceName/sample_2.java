package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ResourcePrefixDetector extends ResourceXmlDetector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project "
                    + "must conform to. This makes it easier to ensure that you don't accidentally "
                    + "combine resources from different libraries, since they all end up in the same "
                    + "shared app namespace.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(ResourcePrefixDetector.class, Scope.ALL_RESOURCE_FILES_SCOPE));

    private static final String ATTR_NAME = "name";
    private static final String TAG_RESOURCES = "resources";
    private static final String TAG_DECLARE_STYLEABLE = "declare-styleable";
    private static final String TAG_ATTR = "attr";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = getPrefix(context);
        if (prefix == null) {
            return;
        }

        if (!isResourceDeclaration(element)) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty() || name.indexOf(':') != -1) {
            return;
        }

        if (!name.startsWith(prefix)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format("Resource name `%1$s` should start with prefix `%2$s`", name, prefix));
        }
    }

    private static boolean isResourceDeclaration(@NonNull Element element) {
        Node parent = element.getParentNode();
        if (!(parent instanceof Element)) {
            return false;
        }

        String parentName = ((Element) parent).getTagName();
        if (TAG_RESOURCES.equals(parentName)) {
            return true;
        }

        return TAG_DECLARE_STYLEABLE.equals(parentName) && TAG_ATTR.equals(element.getTagName());
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        checkFileResource(context, context.file);
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        checkFileResource(context, context.file);
    }

    private static void checkFileResource(@NonNull Context context, @NonNull File file) {
        String prefix = getPrefix(context);
        if (prefix == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(file.getParentFile().getName());
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        String name = getBaseName(file);
        if (name == null || name.startsWith(prefix)) {
            return;
        }

        context.report(
                ISSUE,
                Location.create(file),
                String.format("Resource name `%1$s` should start with prefix `%2$s`", name, prefix));
    }

    @Nullable
    private static String getPrefix(@NonNull Context context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }
        return prefix;
    }

    @Nullable
    private static String getBaseName(@NonNull File file) {
        String name = file.getName();
        int dot = name.indexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.isEmpty() ? null : name;
    }
}