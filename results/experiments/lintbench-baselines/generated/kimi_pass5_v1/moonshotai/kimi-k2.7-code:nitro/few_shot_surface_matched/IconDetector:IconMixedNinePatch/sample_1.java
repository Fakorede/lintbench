package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Mixed Nine-Patch and PNG",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine patch file will both map to the same "
                            + "drawable resource, `@drawable/file`, which is probably not what was "
                            + "intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private final Map<String, ResourceInfo> mResourceMap = new HashMap<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        mResourceMap.clear();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Map.Entry<String, ResourceInfo> entry : mResourceMap.entrySet()) {
            ResourceInfo info = entry.getValue();
            if (info.hasPng && info.hasNinePatch) {
                String message =
                        "The drawable `"
                                + entry.getKey()
                                + "` is defined as both a `.png` and a `.9.png` file";
                context.report(ISSUE, Location.create(info.locationFile), message);
            }
        }
        mResourceMap.clear();
    }

    @Override
    public boolean filterIncident(
            Context context,
            Issue issue,
            Severity severity,
            Location location,
            String message,
            Object data) {
        return true;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {}

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>(4);
        types.add(UClass.class);
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {}

            @Override
            public void visitMethod(UMethod node) {}

            @Override
            public void visitCallExpression(UCallExpression node) {}

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {}
        };
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        super.visitClass(context, declaration);
        checkResourceFolders(context.getProject().getResourceFolders());
    }

    private void checkResourceFolders(List<File> resourceFolders) {
        if (resourceFolders == null) {
            return;
        }
        for (File folder : resourceFolders) {
            collectPngFiles(folder);
        }
    }

    private void collectPngFiles(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectPngFiles(file);
            } else if (file.isFile()) {
                String name = file.getName();
                if (name.endsWith(".9.png")) {
                    String base = name.substring(0, name.length() - ".9.png".length());
                    ResourceInfo info = mResourceMap.get(base);
                    if (info == null) {
                        info = new ResourceInfo(file);
                        mResourceMap.put(base, info);
                    }
                    info.hasNinePatch = true;
                } else if (name.endsWith(".png")) {
                    String base = name.substring(0, name.length() - ".png".length());
                    ResourceInfo info = mResourceMap.get(base);
                    if (info == null) {
                        info = new ResourceInfo(file);
                        mResourceMap.put(base, info);
                    }
                    info.hasPng = true;
                }
            }
        }
    }

    private static class ResourceInfo {
        boolean hasPng;
        boolean hasNinePatch;
        final File locationFile;

        ResourceInfo(File locationFile) {
            this.locationFile = locationFile;
        }
    }
}