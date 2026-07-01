package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
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
import java.util.Arrays;
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

    /** Map from resource name (without extension) to the first file found with that name */
    private final Map<String, File> mFileNames = new HashMap<>();

    public IconDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileNames.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        for (File resDir : resourceFolders) {
            if (!resDir.exists() || !resDir.isDirectory()) {
                continue;
            }
            File[] folders = resDir.listFiles();
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
                // Build a map from base name -> files for this folder
                Map<String, File> pngFiles = new HashMap<>();
                Map<String, File> ninePatchFiles = new HashMap<>();
                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".9.png")) {
                        String baseName = name.substring(0, name.length() - ".9.png".length());
                        ninePatchFiles.put(baseName, file);
                    } else if (name.endsWith(".png")) {
                        String baseName = name.substring(0, name.length() - ".png".length());
                        pngFiles.put(baseName, file);
                    }
                }
                // Find clashes
                for (Map.Entry<String, File> entry : ninePatchFiles.entrySet()) {
                    String baseName = entry.getKey();
                    if (pngFiles.containsKey(baseName)) {
                        File ninePatch = entry.getValue();
                        File png = pngFiles.get(baseName);
                        Location location = Location.create(ninePatch);
                        Location secondary = Location.create(png);
                        secondary.setMessage("Also defined here as a plain PNG");
                        location.setSecondary(secondary);
                        Incident incident = new Incident(
                                ICON_MIXED_NINE_PATCH,
                                location,
                                String.format(
                                        "The files `%1$s` and `%2$s` both define the same"
                                                + " drawable resource `@drawable/%3$s`."
                                                + " One of them should be removed or renamed.",
                                        ninePatch.getName(),
                                        png.getName(),
                                        baseName));
                        context.report(incident);
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull com.android.tools.lint.detector.api.LintMap map) {
        return true;
    }

    // XmlScanner methods

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op: we detect issues via file system inspection in afterCheckEachProject
    }

    // SourceCodeScanner methods

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No-op: icon clash detection is file-system based
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No-op: icon clash detection is file-system based
            }
        };
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No-op
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // No-op
    }
}