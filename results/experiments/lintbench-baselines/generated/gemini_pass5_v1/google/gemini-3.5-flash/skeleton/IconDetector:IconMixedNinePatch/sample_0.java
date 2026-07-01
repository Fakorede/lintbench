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
import com.android.tools.lint.detector.api.ResourceContext;
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
    public void checkFolder(@NonNull ResourceContext context) {
        java.io.File folder = context.getFolder();
        java.io.File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        java.util.Map<String, java.io.File> pngs = new java.util.HashMap<>();
        java.util.Map<String, java.io.File> ninePatches = new java.util.HashMap<>();
        for (java.io.File file : files) {
            String name = file.getName();
            if (name.endsWith(".9.png")) {
                String base = name.substring(0, name.length() - ".9.png".length());
                ninePatches.put(base, file);
            } else if (name.endsWith(".png")) {
                String base = name.substring(0, name.length() - ".png".length());
                pngs.put(base, file);
            }
        }
        for (java.util.Map.Entry<String, java.io.File> entry : ninePatches.entrySet()) {
            String base = entry.getKey();
            if (pngs.containsKey(base)) {
                java.io.File pngFile = pngs.get(base);
                java.io.File ninePatchFile = entry.getValue();
                
                String message = String.format(
                        "Clashing PNG and 9-PNG files: `%s` and `%s` both map to `@drawable/%s`",
                        pngFile.getName(), ninePatchFile.getName(), base);
                
                Incident incident = new Incident(ISSUE, message, context.getLocation(ninePatchFile));
                context.report(incident);
            }
        }
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