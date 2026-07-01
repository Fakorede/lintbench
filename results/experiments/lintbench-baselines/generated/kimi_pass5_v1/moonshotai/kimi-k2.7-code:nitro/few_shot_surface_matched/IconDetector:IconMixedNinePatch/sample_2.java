package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import java.io.File;
import java.util.*;
import org.jetbrains.uast.*;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine patch file will both map to the same "
                            + "drawable resource, `@drawable/file`, which is probably not what was "
                            + "intended.",
                    Category.ICONS,
                    9,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.MANIFEST_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders == null) {
            return;
        }

        Map<String, Set<File>> pngFiles = new HashMap<>();
        Map<String, Set<File>> ninePatchFiles = new HashMap<>();

        for (File res : resourceFolders) {
            if (!res.isDirectory()) {
                continue;
            }
            File[] typeDirs = res.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File dir : typeDirs) {
                String dirName = dir.getName();
                if (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) {
                    continue;
                }
                if (!dir.isDirectory()) {
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
                    String fileName = file.getName();
                    if (fileName.endsWith(".9.png")) {
                        String name = fileName.substring(0, fileName.length() - ".9.png".length());
                        ninePatchFiles.computeIfAbsent(name, k -> new HashSet<>()).add(file);
                    } else if (fileName.endsWith(".png")) {
                        String name = fileName.substring(0, fileName.length() - ".png".length());
                        pngFiles.computeIfAbsent(name, k -> new HashSet<>()).add(file);
                    }
                }
            }
        }

        for (Map.Entry<String, Set<File>> entry : pngFiles.entrySet()) {
            String name = entry.getKey();
            Set<File> ninePatches = ninePatchFiles.get(name);
            if (ninePatches == null || ninePatches.isEmpty()) {
                continue;
            }

            Set<File> pngs = entry.getValue();
            File sample = pngs.isEmpty() ? ninePatches.iterator().next() : pngs.iterator().next();
            String dirName = sample.getParentFile().getName();
            String type = dirName.startsWith("mipmap") ? "mipmap" : "drawable";

            String message =
                    "The image `"
                            + name
                            + ".png` and the nine-patch `"
                            + name
                            + ".9.png` both map to the same resource `@"
                            + type
                            + "/"
                            + name
                            + "`";

            for (File file : pngs) {
                context.report(ISSUE, Location.create(file), message);
            }
            for (File file : ninePatches) {
                context.report(ISSUE, Location.create(file), message);
            }
        }
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        String folder = parent.getName();
        return folder.startsWith("drawable") || folder.startsWith("mipmap");
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
        return Collections.emptyList();
    }
}