package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.ResourceFolder;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class ResourcePrefixDetector extends Detector
        implements XmlScanner, ResourceFolderScanner, SourceCodeScanner {

    public static final Issue RESOURCE_NAME = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resourcePrefix property; all resources "
                    + "in the module should be named with that prefix. This makes it easier to "
                    + "avoid accidental resource collisions when combining modules.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    ResourcePrefixDetector.class,
                    EnumSet.of(
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.RESOURCE_FOLDER_SCOPE,
                            Scope.JAVA_FILE_SCOPE))
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_RESOURCES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        if (!TAG_RESOURCES.equals(element.getTagName())) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            if (!child.hasAttribute(ATTR_NAME)) {
                continue;
            }
            String name = child.getAttribute(ATTR_NAME);
            if (!name.startsWith(prefix)) {
                context.report(
                        RESOURCE_NAME,
                        child.getAttributeNode(ATTR_NAME),
                        context.getLocation(child.getAttributeNode(ATTR_NAME)),
                        String.format(
                                "Resource named `%1$s` does not start with the project prefix `%2$s`",
                                name, prefix));
            }
        }
    }

    @Override
    public void checkFolder(@NonNull ResourceFolder folder, @NonNull Context context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        if (folder.getType() == ResourceFolderType.VALUES) {
            return;
        }

        for (File file : folder.getFiles()) {
            if (!file.isFile() || file.isHidden() || file.getName().startsWith(".")) {
                continue;
            }

            String name = getResourceName(file);
            if (name == null) {
                continue;
            }

            if (!name.startsWith(prefix)) {
                context.report(
                        RESOURCE_NAME,
                        file,
                        context.getLocation(file),
                        String.format(
                                "Resource named `%1$s` in folder `%2$s` does not start with the project prefix `%3$s`",
                                name, folder.getName(), prefix));
            }
        }
    }

    private static String getResourceName(@NonNull File file) {
        String fileName = file.getName();
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }

    @Override
    public void visitElement(@NonNull JavaContext context, @NonNull UElement node) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        if (!(node instanceof UReferenceExpression)) {
            return;
        }

        UReferenceExpression reference = (UReferenceExpression) node;
        PsiElement resolved = reference.resolve();
        if (!(resolved instanceof PsiField)) {
            return;
        }

        PsiField field = (PsiField) resolved;
        PsiClass typeClass = field.getContainingClass();
        if (typeClass == null) {
            return;
        }

        PsiClass rClass = typeClass.getContainingClass();
        if (rClass == null || !"R".equals(rClass.getName())) {
            return;
        }

        String qualifiedName = rClass.getQualifiedName();
        if ("android.R".equals(qualifiedName)) {
            return;
        }

        String name = field.getName();
        if (name != null && !name.startsWith(prefix)) {
            context.report(
                    RESOURCE_NAME,
                    reference,
                    context.getLocation(reference),
                    String.format(
                            "Field `%1$s` in `%2$s` does not start with the project prefix `%3$s`",
                            name, typeClass.getName(), prefix));
        }
    }
}