package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
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
                    new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File resFolder : resourceFolders) {
            File[] subfolders = resFolder.listFiles();
            if (subfolders == null) {
                continue;
            }
            for (File subfolder : subfolders) {
                String name = subfolder.getName();
                if (name.startsWith("drawable")) {
                    checkDrawableFolder(context, subfolder);
                }
            }
        }
    }

    private void checkDrawableFolder(@NonNull Context context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Set<String> regularPngs = new HashSet<>();
        Set<String> ninePatchPngs = new HashSet<>();
        Map<String, File> fileMap = new HashMap<>();

        for (File file : files) {
            String filename = file.getName();
            if (filename.endsWith(".9.png")) {
                String base = filename.substring(0, filename.length() - 6);
                ninePatchPngs.add(base);
                fileMap.put(filename, file);
            } else if (filename.endsWith(".png")) {
                String base = filename.substring(0, filename.length() - 4);
                regularPngs.add(base);
                fileMap.put(filename, file);
            }
        }

        for (String base : ninePatchPngs) {
            if (regularPngs.contains(base)) {
                File pngFile = fileMap.get(base + ".png");
                File ninePatchFile = fileMap.get(base + ".9.png");
                if (pngFile != null && ninePatchFile != null) {
                    Location location = Location.create(pngFile);
                    Location secondary = Location.create(ninePatchFile);
                    location.setSecondary(secondary);
                    String message = String.format(
                            "Clashing PNG and 9-PNG files: `%s.png` and `%s.9.png` both map to `@drawable/%s`",
                            base, base, base
                    );
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @Override
    public void filterIncident(@NonNull Context context, @NonNull Incident incident, @NonNull Map<String, ?> map) {
        // No-op
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        // No-op
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // No-op
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }
}