package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.UElementHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_MIXED_NINE_PATCH =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine patch file will both map to the same "
                            + "drawable resource, `@drawable/file`, which is probably not what was "
                            + "intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, List<FileEntry>> mDrawableFiles;

    private static class FileEntry {
        final File file;
        final boolean ninePatch;

        FileEntry(File file, boolean ninePatch) {
            this.file = file;
            this.ninePatch = ninePatch;
        }
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mDrawableFiles = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        if (project != null) {
            scanResourceFolders(project);
        }

        if (mDrawableFiles == null) {
            return;
        }

        for (Map.Entry<String, List<FileEntry>> entry : mDrawableFiles.entrySet()) {
            File pngFile = null;
            File ninePatchFile = null;

            for (FileEntry e : entry.getValue()) {
                if (e.ninePatch) {
                    ninePatchFile = e.file;
                } else {
                    pngFile = e.file;
                }
            }

            if (pngFile != null && ninePatchFile != null) {
                String message =
                        "The image `"
                                + pngFile.getName()
                                + "` and the nine-patch `"
                                + ninePatchFile.getName()
                                + "` both map to `@drawable/"
                                + entry.getKey()
                                + "`";
                context.report(
                        ICON_MIXED_NINE_PATCH,
                        Location.create(pngFile),
                        message);
            }
        }

        mDrawableFiles.clear();
    }

    private void scanResourceFolders(Project project) {
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders == null) {
            return;
        }

        for (File res : resourceFolders) {
            if (!res.isDirectory()) {
                continue;
            }

            File[] dirs = res.listFiles();
            if (dirs == null) {
                continue;
            }

            for (File dir : dirs) {
                if (!dir.isDirectory()) {
                    continue;
                }

                if (ResourceFolderType.getFolderType(dir.getName()) != ResourceFolderType.DRAWABLE) {
                    continue;
                }

                File[] files = dir.listFiles();
                if (files == null) {
                    continue;
                }

                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }

                    String name = file.getName();
                    if (name.endsWith(".9.png")) {
                        String base = name.substring(0, name.length() - ".9.png".length());
                        addDrawable(base, file, true);
                    } else if (name.endsWith(".png")) {
                        String base = name.substring(0, name.length() - ".png".length());
                        addDrawable(base, file, false);
                    }
                }
            }
        }
    }

    private void addDrawable(String base, File file, boolean ninePatch) {
        List<FileEntry> entries = mDrawableFiles.get(base);
        if (entries == null) {
            entries = new ArrayList<>();
            mDrawableFiles.put(base, entries);
        }
        entries.add(new FileEntry(file, ninePatch));
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, State state) {
        return true;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
            }

            @Override
            public void visitClass(UClass node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
            }
        };
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(UClass.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }
}