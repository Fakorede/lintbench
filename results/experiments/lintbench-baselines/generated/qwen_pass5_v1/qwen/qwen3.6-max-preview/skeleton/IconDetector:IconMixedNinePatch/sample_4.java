package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
                    "the image file and the nine patch file will both map to the same drawable " +
                    "resource, `@drawable/file`, which is probably not what was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        List<File> resFolders = project.getResourceFolders();
        if (resFolders == null || resFolders.isEmpty()) {
            return;
        }

        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();

        for (File resDir : resFolders) {
            File[] drawables = resDir.listFiles((dir, name) -> name.startsWith("drawable"));
            if (drawables == null) continue;

            for (File drawableDir : drawables) {
                if (!drawableDir.isDirectory()) continue;
                File[] files = drawableDir.listFiles();
                if (files == null) continue;

                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".9.png")) {
                        String base = name.substring(0, name.length() - 6);
                        ninePatchFiles.put(base, file);
                    } else if (name.endsWith(".png")) {
                        String base = name.substring(0, name.length() - 4);
                        pngFiles.put(base, file);
                    }
                }
            }
        }

        for (Map.Entry<String, File> entry : pngFiles.entrySet()) {
            String base = entry.getKey();
            File ninePatch = ninePatchFiles.get(base);
            if (ninePatch != null) {
                File png = entry.getValue();
                String message = String.format(
                        "Found both `%1$s.png` and `%1$s.9.png` in the same folder. " +
                        "They will both map to the same drawable resource `@drawable/%1$s`.", base);
                context.report(ISSUE, Location.create(png), message);
                context.report(ISSUE, Location.create(ninePatch), message);
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op: Resource file scanning is handled in afterCheckEachProject
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op: Resource file scanning is handled in afterCheckEachProject
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return null;
    }
}