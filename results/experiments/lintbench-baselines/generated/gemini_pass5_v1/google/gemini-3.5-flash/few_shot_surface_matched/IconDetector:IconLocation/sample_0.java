package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and "
                            + "consider providing higher and lower resolution versions in "
                            + "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon "
                            + "really is density independent (for example a solid color) you can place "
                            + "it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident, @NonNull Context context) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        if (file.isFile()) {
            File parentFile = file.getParentFile();
            if (parentFile != null && "drawable".equals(parentFile.getName())) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp")) {
                    Incident incident = new Incident(
                            ISSUE,
                            "Consider moving this bitmap to a density-specific folder",
                            Location.create(file)
                    );
                    context.report(incident);
                }
            }
        }
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        File file = context.file;
        File parentFile = file.getParentFile();
        if (parentFile != null && "drawable".equals(parentFile.getName())) {
            Incident incident = new Incident(
                    ISSUE,
                    "Consider moving this bitmap XML to a density-specific folder",
                    context.getLocation(element)
            );
            context.report(incident);
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class,
                UMethod.class,
                UCallExpression.class,
                USimpleNameReferenceExpression.class
        );
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitMethod(@NonNull UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // Sourcecode analysis hook if needed
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Sourcecode analysis hook if needed
    }

    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // Sourcecode analysis hook if needed
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Sourcecode analysis hook if needed
    }
}