package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_MIXED_NINE_PATCH =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`,"
                            + " the image file and the nine patch file will both map to the same"
                            + " drawable resource, `@drawable/file`, which is probably not what"
                            + " was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.ALL_RESOURCES_SCOPE));

    // Map from resource name (without extension) to list of files that map to it
    private final Map<String, List<File>> mResourceMap = new HashMap<>();

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mResourceMap.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        // Scan drawable directories for clashing PNG and 9-PNG files
        List<File> resourceFolders = project.getResourceFolders();
        for (File resFolder : resourceFolders) {
            if (!resFolder.exists()) {
                continue;
            }
            File[] folders = resFolder.listFiles();
            if (folders == null) {
                continue;
            }
            for (File folder : folders) {
                String folderName = folder.getName();
                if (!folderName.startsWith("drawable")) {
                    continue;
                }
                File[] files = folder.listFiles();
                if (files == null) {
                    continue;
                }
                // Collect png and 9.png files keyed by base resource name
                Map<String, List<File>> localMap = new HashMap<>();
                for (File file : files) {
                    String name = file.getName();
                    String baseName = null;
                    if (name.endsWith(".9.png")) {
                        baseName = name.substring(0, name.length() - ".9.png".length());
                    } else if (name.endsWith(".png")) {
                        baseName = name.substring(0, name.length() - ".png".length());
                    }
                    if (baseName != null) {
                        List<File> list = localMap.get(baseName);
                        if (list == null) {
                            list = new ArrayList<>();
                            localMap.put(baseName, list);
                        }
                        list.add(file);
                    }
                }
                // Check for clashes
                for (Map.Entry<String, List<File>> entry : localMap.entrySet()) {
                    List<File> fileList = entry.getValue();
                    if (fileList.size() >= 2) {
                        boolean hasPng = false;
                        boolean hasNinePatch = false;
                        for (File f : fileList) {
                            if (f.getName().endsWith(".9.png")) {
                                hasNinePatch = true;
                            } else if (f.getName().endsWith(".png")) {
                                hasPng = true;
                            }
                        }
                        if (hasPng && hasNinePatch) {
                            String baseName = entry.getKey();
                            // Find the two files for location reporting
                            File pngFile = null;
                            File ninePatchFile = null;
                            for (File f : fileList) {
                                if (f.getName().endsWith(".9.png")) {
                                    ninePatchFile = f;
                                } else if (f.getName().endsWith(".png")) {
                                    pngFile = f;
                                }
                            }
                            Location location = Location.create(ninePatchFile != null ? ninePatchFile : fileList.get(0));
                            if (pngFile != null && ninePatchFile != null) {
                                location = Location.create(ninePatchFile)
                                        .withSecondary(Location.create(pngFile), "Also maps to @drawable/" + baseName);
                            }
                            Incident incident = new Incident(
                                    ICON_MIXED_NINE_PATCH,
                                    location,
                                    "The files `" + baseName + ".png` and `" + baseName
                                            + ".9.png` both map to the same drawable resource"
                                            + " `@drawable/" + baseName + "`");
                            context.report(incident);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull com.android.tools.lint.detector.api.LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element visiting needed for this check
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    @Nullable
    public com.android.tools.lint.detector.api.UastHandler createUastHandler(@NonNull JavaContext context) {
        return null;
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Not used
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression expression) {
        // Not used
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // Not used
    }
}