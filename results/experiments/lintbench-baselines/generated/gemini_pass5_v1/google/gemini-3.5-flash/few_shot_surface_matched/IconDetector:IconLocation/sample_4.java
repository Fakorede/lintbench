package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;
import java.io.File;
import java.util.Collection;
import java.util.List;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                            + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                            + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                            + "a solid color) you can place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void afterCheckEachProject(Context context) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders == null) {
            return;
        }
        for (File res : resourceFolders) {
            if (res == null) {
                continue;
            }
            File drawableDir = new File(res, "drawable");
            if (drawableDir.isDirectory()) {
                File[] files = drawableDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            String name = file.getName().toLowerCase();
                            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp")) {
                                context.report(ISSUE, Location.create(file),
                                        "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon really is density independent (for example a solid color) you can place it in `drawable-nodpi`.");
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void filterIncident(Incident incident, Context context) {
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return null;
    }

    public void visitMethod(JavaContext context, UMethod method) {
    }

    public void visitCallExpression(JavaContext context, UCallExpression node) {
    }

    public void visitClass(JavaContext context, UClass declaration) {
    }

    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }
}