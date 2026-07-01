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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE)));

    // Map from resource name (without extension) to the files that map to it
    // Key: resource name, Value: map from folder to list of conflicting files
    private Map<String, Map<String, File[]>> mResources;

    public IconDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mResources = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mResources == null) {
            return;
        }

        Project project = context.getProject();
        List<File> resDirs = project.getResourceFolders();

        for (File resDir : resDirs) {
            if (!resDir.exists()) {
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

                // Collect PNG and 9-patch files, group by resource name
                Map<String, File> pngFiles = new HashMap<>();
                Map<String, File> ninePatchFiles = new HashMap<>();

                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".9.png")) {
                        String resourceName = name.substring(0, name.length() - ".9.png".length());
                        ninePatchFiles.put(resourceName, file);
                    } else if (name.endsWith(".png")) {
                        String resourceName = name.substring(0, name.length() - ".png".length());
                        pngFiles.put(resourceName, file);
                    }
                }

                // Find clashes
                for (String resourceName : pngFiles.keySet()) {
                    if (ninePatchFiles.containsKey(resourceName)) {
                        File pngFile = pngFiles.get(resourceName);
                        File ninePatchFile = ninePatchFiles.get(resourceName);

                        Location location = Location.create(pngFile);
                        Location secondary = Location.create(ninePatchFile);
                        secondary.setMessage("Nine-patch file here");
                        location.setSecondary(secondary);

                        Incident incident =
                                new Incident(
                                        ICON_MIXED_9PATCH,
                                        location,
                                        String.format(
                                                "The files `%1$s` and `%2$s` both map to"
                                                        + " the same drawable resource"
                                                        + " `@drawable/%3$s`",
                                                pngFile.getName(),
                                                ninePatchFile.getName(),
                                                resourceName));
                        context.report(incident);
                    }
                }
            }
        }

        mResources = null;
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
        // Not used for this detector
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
                // Not used for this detector's primary purpose
            }
        };
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
        // Not used for this detector
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        // Not used for this detector
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this detector
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression expression) {
        // Not used for this detector
    }
}