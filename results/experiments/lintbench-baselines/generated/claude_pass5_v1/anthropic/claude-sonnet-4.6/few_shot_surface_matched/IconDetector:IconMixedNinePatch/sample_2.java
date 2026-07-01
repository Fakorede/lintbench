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

    /** Map from base name (without extension) to the file that defines it, per project. */
    private Map<String, File> mPngFiles;
    private Map<String, File> mNinePatchFiles;

    public IconDetector() {}

    // -----------------------------------------------------------------------
    // Detector lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mPngFiles = new HashMap<>();
        mNinePatchFiles = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mPngFiles == null || mNinePatchFiles == null) {
            return;
        }

        Project project = context.getProject();

        for (Map.Entry<String, File> entry : mNinePatchFiles.entrySet()) {
            String baseName = entry.getKey();
            if (mPngFiles.containsKey(baseName)) {
                File ninePatchFile = entry.getValue();
                File pngFile = mPngFiles.get(baseName);

                String message =
                        String.format(
                                "The files `%1$s` and `%2$s` both map to the drawable resource"
                                        + " `@drawable/%3$s`; rename or delete one of them",
                                ninePatchFile.getName(),
                                pngFile.getName(),
                                baseName);

                Location location = Location.create(ninePatchFile);
                Location secondary = Location.create(pngFile);
                secondary.setMessage("This file also maps to `@drawable/" + baseName + "`");
                location.setSecondary(secondary);

                Incident incident =
                        new Incident(ICON_MIXED_NINE_PATCH, location, message);
                context.report(incident);
            }
        }

        mPngFiles.clear();
        mNinePatchFiles.clear();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull com.android.tools.lint.detector.api.LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    // -----------------------------------------------------------------------
    // Resource file scanning — we scan every file in drawable / mipmap folders
    // -----------------------------------------------------------------------

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();

        if (name.endsWith(".9.png")) {
            // nine-patch: strip ".9.png"
            String baseName = name.substring(0, name.length() - ".9.png".length());
            if (mNinePatchFiles != null) {
                mNinePatchFiles.put(baseName, file);
            }
        } else if (name.endsWith(".png")) {
            // plain PNG: strip ".png"
            String baseName = name.substring(0, name.length() - ".png".length());
            if (mPngFiles != null) {
                mPngFiles.put(baseName, file);
            }
        }
    }

    // -----------------------------------------------------------------------
    // XmlScanner — required by interface; no XML elements to visit for this check
    // -----------------------------------------------------------------------

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element visiting needed for this check
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner — required by interface; no source scanning needed
    // -----------------------------------------------------------------------

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return null;
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No method visiting needed for this check
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // No call expression visiting needed for this check
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class visiting needed for this check
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // No reference expression visiting needed for this check
    }
}