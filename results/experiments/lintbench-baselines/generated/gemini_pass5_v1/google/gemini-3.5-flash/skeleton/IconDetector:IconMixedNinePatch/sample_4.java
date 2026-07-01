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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner, Detector.ResourceFolderScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        java.util.Map<String, java.util.List<java.io.File>> pngFiles = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<java.io.File>> ninePatchFiles = new java.util.HashMap<>();

        for (java.io.File resFolder : resourceFolders) {
            java.io.File[] subFolders = resFolder.listFiles();
            if (subFolders == null) continue;
            for (java.io.File subFolder : subFolders) {
                String name = subFolder.getName();
                if (name.startsWith("drawable")) {
                    java.io.File[] files = subFolder.listFiles();
                    if (files == null) continue;
                    for (java.io.File file : files) {
                        String fileName = file.getName();
                        if (fileName.endsWith(".9.png")) {
                            String resName = fileName.substring(0, fileName.length() - 6);
                            ninePatchFiles.computeIfAbsent(resName, k -> new java.util.ArrayList<>()).add(file);
                        } else if (fileName.endsWith(".png")) {
                            String resName = fileName.substring(0, fileName.length() - 4);
                            pngFiles.computeIfAbsent(resName, k -> new java.util.ArrayList<>()).add(file);
                        }
                    }
                }
            }
        }

        for (String resName : ninePatchFiles.keySet()) {
            if (pngFiles.containsKey(resName)) {
                List<java.io.File> nps = ninePatchFiles.get(resName);
                List<java.io.File> pngs = pngFiles.get(resName);
                for (java.io.File np : nps) {
                    Incident incident = new Incident(ISSUE, "Clashing PNG and 9-PNG files; both map to `@drawable/" + resName + "`", context.getLocation(np));
                    context.report(incident);
                }
                for (java.io.File png : pngs) {
                    Incident incident = new Incident(ISSUE, "Clashing PNG and 9-PNG files; both map to `@drawable/" + resName + "`", context.getLocation(png));
                    context.report(incident);
                }
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
        return null;
    }
}