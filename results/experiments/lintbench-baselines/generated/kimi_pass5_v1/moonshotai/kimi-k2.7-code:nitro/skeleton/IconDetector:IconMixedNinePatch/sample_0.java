package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine-patch file will both map to the same "
                            + "drawable resource, `@drawable/file`, which is probably not what was "
                            + "intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<File, Map<String, File>> mFolderBaseNames = new HashMap<>();
    private final Set<String> mReportedClashes = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFolderBaseNames.clear();
        mReportedClashes.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mFolderBaseNames.clear();
        mReportedClashes.clear();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used for this issue.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this issue.
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
                // Not used for this issue.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for this issue.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for this issue.
            }
        };
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType, @NonNull String fileName) {
        return folderType == ResourceFolderType.DRAWABLE && fileName.endsWith(".png");
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String fileName = file.getName();
        if (!fileName.endsWith(".png")) {
            return;
        }

        String baseName = getBaseName(fileName);
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        Map<String, File> baseNames = mFolderBaseNames.get(folder);
        if (baseNames == null) {
            baseNames = new HashMap<>();
            mFolderBaseNames.put(folder, baseNames);
        }

        File existing = baseNames.get(baseName);
        if (existing != null && !existing.equals(file)) {
            String key = folder.getPath() + "/" + baseName;
            if (mReportedClashes.add(key)) {
                String message =
                        String.format(
                                "The resources `%1$s` and `%2$s` both map to the same drawable resource `%3$s`",
                                existing.getName(), fileName, baseName);
                Incident incident = new Incident(ISSUE, Location.create(file), message);
                incident.at(Location.create(existing));
                context.report(incident);
            }
        } else {
            baseNames.put(baseName, file);
        }
    }

    private static String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        if (fileName.endsWith(".png")) {
            return fileName.substring(0, fileName.length() - ".png".length());
        }
        return fileName;
    }
}