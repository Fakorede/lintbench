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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
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

    private static final String TAG_BITMAP = "bitmap";
    private static final String TAG_NINE_PATCH = "nine-patch";

    private static final String EXPLANATION =
            "The res/drawable folder is intended for density-independent graphics such as shapes "
                    + "defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                    + "providing higher and lower resolution versions in `drawable-ldpi`, "
                    + "`drawable-hdpi` and `drawable-xhdpi`. If the icon really is density "
                    + "independent (for example a solid color) you can place it in `drawable-nodpi`.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    EXPLANATION,
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No per-project state is required for this check.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No post-processing is required for this check.
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
        return Arrays.asList(TAG_BITMAP, TAG_NINE_PATCH);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isRootElement(element)) {
            return;
        }

        File parent = context.file.getParentFile();
        if (parent == null) {
            return;
        }

        if ("drawable".equals(parent.getName())) {
            context.report(ISSUE, element, context.getNameLocation(element), EXPLANATION);
        }
    }

    private static boolean isRootElement(@NonNull Element element) {
        return element == element.getOwnerDocument().getDocumentElement();
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for the IconLocation check.
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
                // Not used for the IconLocation check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for the IconLocation check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for the IconLocation check.
            }
        };
    }
}