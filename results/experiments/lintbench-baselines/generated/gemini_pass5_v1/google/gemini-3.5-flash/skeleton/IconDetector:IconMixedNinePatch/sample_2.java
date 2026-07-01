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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
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

    private boolean mChecked = false;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        if (!mChecked) {
            checkProjects(context);
            mChecked = true;
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!mChecked) {
            checkProjects(context);
            mChecked = true;
        }
    }

    private void checkProjects(@NonNull Context context) {
        List<Project> projects;
        try {
            projects = context.getDriver().getProjects();
        } catch (Throwable t) {
            projects = Collections.singletonList(context.getProject());
        }

        for (Project project : projects) {
            checkProject(context, project);
        }
    }

    private void checkProject(@NonNull Context context, @NonNull Project project) {
        Map<String, List<File>> ninePatches = new HashMap<>();
        Map<String, List<File>> regularPngs = new HashMap<>();

        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders == null) {
            return;
        }

        for (File resFolder : resourceFolders) {
            File[] subdirs = resFolder.listFiles();
            if (subdirs == null) {
                continue;
            }
            for (File subdir : subdirs) {
                String folderName = subdir.getName();
                if (folderName.startsWith("drawable")) {
                    File[] files = subdir.listFiles();
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        String filename = file.getName();
                        if (filename.endsWith(".9.png")) {
                            String name = filename.substring(0, filename.length() - ".9.png".length());
                            if (!ninePatches.containsKey(name)) {
                                ninePatches.put(name, new ArrayList<>());
                            }
                            ninePatches.get(name).add(file);
                        } else if (filename.endsWith(".png")) {
                            String name = filename.substring(0, filename.length() - ".png".length());
                            if (!regularPngs.containsKey(name)) {
                                regularPngs.put(name, new ArrayList<>());
                            }
                            regularPngs.get(name).add(file);
                        }
                    }
                }
            }
        }

        for (String name : ninePatches.keySet()) {
            if (regularPngs.containsKey(name)) {
                List<File> nps = ninePatches.get(name);
                List<File> pgs = regularPngs.get(name);

                if (nps != null) {
                    for (File file : nps) {
                        reportClash(context, file, name);
                    }
                }
                if (pgs != null) {
                    for (File file : pgs) {
                        reportClash(context, file, name);
                    }
                }
            }
        }
    }

    private void reportClash(@NonNull Context context, @NonNull File file, @NonNull String resourceName) {
        String message = "Clashing PNG and 9-PNG files: regular PNG and 9-patch PNG both map to `@drawable/" + resourceName + "`";
        Incident incident = new Incident();
        incident.setIssue(ISSUE);
        incident.setLocation(Location.create(file));
        incident.setMessage(message);
        context.report(incident);
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