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

public class IconDetector extends Detector
        implements SourceCodeScanner, XmlScanner, ResourceFolderScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If both `file.png` and `file.9.png` exist in the drawable folders they both "
                            + "map to the same `@drawable/file` resource, which is usually a mistake.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String DOT_PNG = ".png";
    private static final String DOT_9PNG = ".9.png";

    private Map<String, List<File>> mNames;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<File>> entry : mNames.entrySet()) {
            List<File> files = entry.getValue();
            boolean hasNinePatch = false;
            boolean hasPlainPng = false;
            for (File file : files) {
                if (file.getName().endsWith(DOT_9PNG)) {
                    hasNinePatch = true;
                } else if (file.getName().endsWith(DOT_PNG)) {
                    hasPlainPng = true;
                }
            }

            if (hasNinePatch && hasPlainPng) {
                File file = files.get(0);
                Location location = Location.create(file);
                String message =
                        String.format(
                                "The resource `@drawable/%1$s` is defined by both a PNG file and a 9-patch file: %2$s",
                                entry.getKey(), files);
                Incident incident = new Incident(ISSUE, location, message);
                context.report(incident);
            }
        }

        mNames.clear();
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
    public void checkFile(@NonNull ResourceContext context, @NonNull File file) {
        String name = file.getName();
        String base = null;
        if (name.endsWith(DOT_9PNG)) {
            base = name.substring(0, name.length() - DOT_9PNG.length());
        } else if (name.endsWith(DOT_PNG)) {
            base = name.substring(0, name.length() - DOT_PNG.length());
        }

        if (base != null) {
            if (mNames == null) {
                mNames = new HashMap<>();
            }
            List<File> files = mNames.computeIfAbsent(base, k -> new ArrayList<>());
            files.add(file);
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used for this check.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this check.
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
                // Not used for this check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for this check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for this check.
            }
        };
    }
}