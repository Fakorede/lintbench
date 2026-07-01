package com.android.tools.lint.checks;

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
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_MIXED_NINE_PATCH =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine patch file will both map to the same "
                            + "drawable resource, `@drawable/file`, which is probably not what "
                            + "was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private final Map<String, File> mBaseNames = new HashMap<>();
    private final Set<String> mReported = new HashSet<>();

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(Context context) {
        mBaseNames.clear();
        mReported.clear();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        mBaseNames.clear();
        mReported.clear();

        List<File> resourceDirectories = context.getProject().getResourceDirectories();
        if (resourceDirectories == null) {
            return;
        }

        for (File resDir : resourceDirectories) {
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
                for (File file : files) {
                    String fileName = file.getName();
                    if (!fileName.endsWith(".png") && !fileName.endsWith(".9.png")) {
                        continue;
                    }

                    boolean isNinePatch = fileName.endsWith(".9.png");
                    String baseName;
                    if (isNinePatch) {
                        baseName = fileName.substring(0, fileName.length() - ".9.png".length());
                    } else {
                        baseName = fileName.substring(0, fileName.length() - ".png".length());
                    }

                    File existing = mBaseNames.get(baseName);
                    if (existing != null) {
                        boolean existingIsNinePatch = existing.getName().endsWith(".9.png");
                        if (existingIsNinePatch != isNinePatch && !mReported.contains(baseName)) {
                            mReported.add(baseName);
                            Location location =
                                    Location.create(file)
                                            .withSecondary(
                                                    Location.create(existing),
                                                    "Conflicting icon");
                            context.report(
                                    ICON_MIXED_NINE_PATCH,
                                    location,
                                    "The image `"
                                            + existing.getName()
                                            + "` and the nine-patch image `"
                                            + fileName
                                            + "` both map to the same `@drawable/"
                                            + baseName
                                            + "` resource");
                        }
                    } else {
                        mBaseNames.put(baseName, file);
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {}

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class, UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new IconHandler(context);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {}

    @Override
    public void visitMethod(JavaContext context, UMethod method, PsiMethod psiMethod) {}

    private static class IconHandler extends UElementHandler {
        private final JavaContext mContext;

        IconHandler(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitClass(UClass node) {}

        @Override
        public void visitMethod(UMethod node) {}

        @Override
        public void visitCallExpression(UCallExpression node) {}

        @Override
        public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {}
    }
}