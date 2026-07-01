package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine patch file will both map to the same drawable "
                            + "resource, `@drawable/file`, which is probably not what was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        for (File resDir : project.getResourceFolders()) {
            File[] densityDirs = resDir.listFiles();
            if (densityDirs == null) continue;
            for (File dir : densityDirs) {
                if (dir.isDirectory() && dir.getName().startsWith("drawable")) {
                    File[] files = dir.listFiles();
                    if (files == null) continue;
                    Set<String> pngNames = new HashSet<>();
                    Set<String> ninePatchNames = new HashSet<>();
                    Map<String, File> nameToFile = new HashMap<>();

                    for (File file : files) {
                        String name = file.getName();
                        if (name.endsWith(".9.png")) {
                            String baseName = name.substring(0, name.length() - 6);
                            ninePatchNames.add(baseName);
                            nameToFile.put(name, file);
                        } else if (name.endsWith(".png")) {
                            String baseName = name.substring(0, name.length() - 4);
                            pngNames.add(baseName);
                            nameToFile.put(name, file);
                        }
                    }

                    for (String baseName : pngNames) {
                        if (ninePatchNames.contains(baseName)) {
                            File pngFile = nameToFile.get(baseName + ".png");
                            File ninePatchFile = nameToFile.get(baseName + ".9.png");
                            if (pngFile != null && ninePatchFile != null) {
                                String message = String.format(
                                        "Clashing PNG and 9-PNG files: `%s` and `%s` both map to `@drawable/%s`",
                                        pngFile.getName(), ninePatchFile.getName(), baseName);
                                Location location = Location.create(pngFile);
                                context.report(ISSUE, location, message);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void filterIncident(Incident incident, Context context) {
        super.filterIncident(incident, context);
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
    }

    @Override
    public com.android.tools.lint.client.api.UElementHandler createUastHandler(JavaContext context) {
        return null;
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression call, PsiMethod method) {
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }
}