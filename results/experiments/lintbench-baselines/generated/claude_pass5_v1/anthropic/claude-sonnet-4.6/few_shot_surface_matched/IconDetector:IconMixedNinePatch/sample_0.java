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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_MIXED_9PATCH =
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

    /** Map from base name to set of files that map to that name, per project */
    private Map<String, Set<File>> mFileMap;

    public IconDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileMap = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mFileMap == null || mFileMap.isEmpty()) {
            return;
        }

        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();

        for (File resDir : resourceFolders) {
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

                // Map from base resource name to set of filenames found
                Map<String, Set<String>> nameToFiles = new HashMap<>();

                for (File f : files) {
                    String fileName = f.getName();
                    String baseName = getBaseName(fileName);
                    if (baseName == null) {
                        continue;
                    }
                    Set<String> fileNames = nameToFiles.get(baseName);
                    if (fileNames == null) {
                        fileNames = new HashSet<>();
                        nameToFiles.put(baseName, fileNames);
                    }
                    fileNames.add(fileName);
                }

                for (Map.Entry<String, Set<String>> entry : nameToFiles.entrySet()) {
                    Set<String> fileNames = entry.getValue();
                    if (fileNames.size() < 2) {
                        continue;
                    }

                    boolean hasPng = false;
                    boolean hasNinePatch = false;
                    for (String fileName : fileNames) {
                        if (fileName.endsWith(".9.png")) {
                            hasNinePatch = true;
                        } else if (fileName.endsWith(".png")) {
                            hasPng = true;
                        }
                    }

                    if (hasPng && hasNinePatch) {
                        String baseName = entry.getKey();
                        Location location = Location.create(folder);
                        context.report(
                                new Incident(
                                        ICON_MIXED_9PATCH,
                                        location,
                                        String.format(
                                                "The files `%1$s.png` and `%1$s.9.png` both"
                                                        + " map to the same drawable resource"
                                                        + " `@drawable/%1$s`",
                                                baseName)));
                    }
                }
            }
        }

        mFileMap = null;
    }

    /**
     * Returns the base resource name for a drawable file, stripping the extension.
     * For "foo.9.png" returns "foo", for "foo.png" returns "foo", for other files returns null.
     */
    @Nullable
    private static String getBaseName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        } else if (fileName.endsWith(".png")) {
            return fileName.substring(0, fileName.length() - ".png".length());
        }
        return null;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull Object cookie) {
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
        // No-op for this detector
    }

    // SourceCodeScanner methods

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No-op for this detector
            }
        };
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No-op for this detector
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression call) {
        // No-op for this detector
    }

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.emptyList();
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this detector
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // No-op for this detector
    }
}