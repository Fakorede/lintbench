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
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
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

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner, ResourceFolderScanner {

    private static final String EXT_PNG = ".png";
    private static final String EXT_9PNG = ".9.png";

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine patch file will both map to the same "
                            + "drawable resource, `@drawable/file`, which is probably not what was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<File, Map<String, NameFiles>> mDrawableFiles;

    private static class NameFiles {
        File png;
        File ninePatch;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mDrawableFiles = new HashMap<>();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitResource(@NonNull ResourceContext context) {
        File file = context.getFile();
        String fileName = file.getName();
        String baseName;
        boolean isNinePatch;
        if (fileName.endsWith(EXT_9PNG)) {
            isNinePatch = true;
            baseName = fileName.substring(0, fileName.length() - EXT_9PNG.length());
        } else if (fileName.endsWith(EXT_PNG)) {
            isNinePatch = false;
            baseName = fileName.substring(0, fileName.length() - EXT_PNG.length());
        } else {
            return;
        }

        File folder = file.getParentFile();
        Map<String, NameFiles> names = mDrawableFiles.get(folder);
        if (names == null) {
            names = new HashMap<>();
            mDrawableFiles.put(folder, names);
        }
        NameFiles holder = names.get(baseName);
        if (holder == null) {
            holder = new NameFiles();
            names.put(baseName, holder);
        }
        if (isNinePatch) {
            holder.ninePatch = file;
        } else {
            holder.png = file;
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mDrawableFiles == null) {
            return;
        }
        for (Map<String, NameFiles> names : mDrawableFiles.values()) {
            for (Map.Entry<String, NameFiles> entry : names.entrySet()) {
                NameFiles files = entry.getValue();
                if (files.png != null && files.ninePatch != null) {
                    String name = entry.getKey();
                    String message = String.format(
                            "The `%1$s.png` and `%1$s.9.png` files both resolve to the same drawable resource, `@drawable/%1$s`",
                            name);
                    Location location = Location.create(files.png)
                            .withSecondary(Location.create(files.ninePatch), "The 9-patch definition");
                    context.report(new Incident(ISSUE, location, message));
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
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
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