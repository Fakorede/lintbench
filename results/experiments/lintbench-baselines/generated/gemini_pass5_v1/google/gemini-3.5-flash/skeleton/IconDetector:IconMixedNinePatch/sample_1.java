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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Collection;
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
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!context.isEnabled(ISSUE)) {
            return;
        }
        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File res : resourceFolders) {
            File[] subdirs = res.listFiles();
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    if (subdir.isDirectory() && subdir.getName().startsWith("drawable")) {
                        checkFolder(context, subdir);
                    }
                }
            }
        }
    }

    private void checkFolder(@NonNull Context context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".9.png")) {
                String resName = name.substring(0, name.length() - 6);
                ninePatchFiles.put(resName, file);
            } else if (name.endsWith(".png")) {
                String resName = name.substring(0, name.length() - 4);
                pngFiles.put(resName, file);
            }
        }

        for (Map.Entry<String, File> entry : ninePatchFiles.entrySet()) {
            String resName = entry.getKey();
            if (pngFiles.containsKey(resName)) {
                File ninePatchFile = entry.getValue();
                File pngFile = pngFiles.get(resName);
                Location location = Location.create(pngFile);
                location.setSecondary(Location.create(ninePatchFile));
                String message = String.format(
                        "Clashing PNG and 9-PNG files: `%s` and `%s` both map to `@drawable/%s`",
                        pngFile.getName(), ninePatchFile.getName(), resName);
                Incident incident = new Incident(ISSUE, location, message);
                context.report(incident);
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
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
            }
            
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
            }
            
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
            }
        };
    }
}