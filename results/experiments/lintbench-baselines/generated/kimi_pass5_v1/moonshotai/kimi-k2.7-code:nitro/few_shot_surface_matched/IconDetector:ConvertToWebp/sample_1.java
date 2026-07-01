package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1"
                            + " it supports transparency and lossless conversion as well. Note that"
                            + " there is a quickfix in the IDE which lets you perform conversion."
                            + "\n\nPreviously, launcher icons were required to be in the PNG format"
                            + " but that restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    private final Map<String, List<File>> mPendingIcons = new HashMap<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        mPendingIcons.clear();
        Project project = context.getProject();
        scanProject(project);
        for (Project lib : project.getAllLibraries()) {
            scanProject(lib);
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        File projectDir = context.getProject().getDir();
        String projectPath = projectDir.getAbsolutePath();
        for (List<File> files : mPendingIcons.values()) {
            for (File file : files) {
                if (file.getAbsolutePath().startsWith(projectPath)) {
                    context.report(
                            ISSUE,
                            Location.create(file),
                            "Convert `"
                                    + file.getName()
                                    + "` to WebP for a smaller image size");
                }
            }
        }
    }

    @Override
    public boolean filterIncident(LintDriver driver, Incident incident, Object scope, Issue issue) {
        return true;
    }

    @Override
    public boolean appliesTo(
            com.android.resources.ResourceFolderType folderType, String fileName) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.MIPMAP
                || folderType == com.android.resources.ResourceFolderType.LAYOUT
                || folderType == com.android.resources.ResourceFolderType.MENU
                || folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getAttributes() == null) {
            return;
        }
        for (int i = 0, n = element.getAttributes().getLength(); i < n; i++) {
            Attr attr = (Attr) element.getAttributes().item(i);
            if (attr == null) {
                continue;
            }
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
            }
            if (!isDrawableAttribute(localName)) {
                continue;
            }
            String name = getDrawableResourceName(attr.getValue());
            if (name == null) {
                continue;
            }
            List<File> files = mPendingIcons.get(name);
            if (files == null || files.isEmpty()) {
                continue;
            }
            File file = files.get(0);
            context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Convert `" + file.getName() + "` to WebP for a smaller image size");
            mPendingIcons.remove(name);
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitMethod(UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.<Class<? extends UElement>>asList(
                UClass.class, UMethod.class, UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    private void visitClass(JavaContext context, UClass node) {
        // no WebP-specific class checks needed
    }

    private void visitMethod(JavaContext context, UMethod node) {
        // no WebP-specific method checks needed
    }

    private void visitCallExpression(JavaContext context, UCallExpression node) {
        // drawable references are handled by visitSimpleNameReferenceExpression
    }

    private void visitSimpleNameReferenceExpression(
            JavaContext context, USimpleNameReferenceExpression node) {
        PsiElement resolved = node.resolve();
        if (!(resolved instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) resolved;
        PsiClass cls = field.getContainingClass();
        if (cls == null) {
            return;
        }
        String typeName = cls.getName();
        if (!"drawable".equals(typeName) && !"mipmap".equals(typeName)) {
            return;
        }
        PsiClass parent = cls.getContainingClass();
        if (parent == null || !"R".equals(parent.getName())) {
            return;
        }
        String name = field.getName();
        List<File> files = mPendingIcons.get(name);
        if (files == null || files.isEmpty()) {
            return;
        }
        File file = files.get(0);
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Convert `" + file.getName() + "` to WebP for a smaller image size");
        mPendingIcons.remove(name);
    }

    private void scanProject(Project project) {
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders == null) {
            return;
        }
        for (File res : resourceFolders) {
            if (!res.isDirectory()) {
                continue;
            }
            File[] typeDirs = res.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File dir : typeDirs) {
                String dirName = dir.getName();
                if (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) {
                    continue;
                }
                File[] files = dir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    String fileName = file.getName().toLowerCase();
                    if (fileName.endsWith(".9.png")
                            || fileName.endsWith(".xml")
                            || fileName.endsWith(".webp")) {
                        continue;
                    }
                    if (fileName.endsWith(".png")
                            || fileName.endsWith(".jpg")
                            || fileName.endsWith(".jpeg")) {
                        int dot = fileName.lastIndexOf('.');
                        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
                        mPendingIcons
                                .computeIfAbsent(baseName, k -> new ArrayList<>())
                                .add(file);
                    }
                }
            }
        }
    }

    private static boolean isDrawableAttribute(String name) {
        return "src".equals(name)
                || "background".equals(name)
                || "drawable".equals(name)
                || "icon".equals(name)
                || "logo".equals(name)
                || "srcCompat".equals(name)
                || "thumb".equals(name)
                || "track".equals(name);
    }

    private static String getDrawableResourceName(String value) {
        if (value == null) {
            return null;
        }
        int slash = value.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String prefix = value.substring(0, slash);
        if (!prefix.contains("drawable") && !prefix.contains("mipmap")) {
            return null;
        }
        return value.substring(slash + 1);
    }
}