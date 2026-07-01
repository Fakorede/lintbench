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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Location;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
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
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. For bitmaps, move it to drawable-mdpi and consider providing higher and lower resolution versions in drawable-ldpi, drawable-hdpi and drawable-xhdpi. If the icon really is density independent (for example a solid color) you can place it in drawable-nodpi.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        checkProject(context);
    }

    private void checkProject(@NonNull Context context) {
        List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File res : resourceFolders) {
            java.io.File[] subfolders = res.listFiles();
            if (subfolders != null) {
                for (java.io.File folder : subfolders) {
                    if (folder.isDirectory()) {
                        String folderName = folder.getName();
                        if ((folderName.equals("drawable") || folderName.startsWith("drawable-"))
                                && !isDensityFolder(folderName)) {
                            java.io.File[] files = folder.listFiles();
                            if (files != null) {
                                for (java.io.File file : files) {
                                    String name = file.getName();
                                    if (isBitmap(name)) {
                                        Incident incident = new Incident(ISSUE);
                                        incident.setLocation(Location.create(file));
                                        incident.setMessage("The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon really is density independent (for example a solid color) you can place it in `drawable-nodpi`.");
                                        context.report(incident);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isDensityFolder(String folderName) {
        return folderName.contains("-ldpi") ||
               folderName.contains("-mdpi") ||
               folderName.contains("-hdpi") ||
               folderName.contains("-xhdpi") ||
               folderName.contains("-xxhdpi") ||
               folderName.contains("-xxxhdpi") ||
               folderName.contains("-tvdpi") ||
               folderName.contains("-nodpi") ||
               folderName.contains("-anydpi");
    }

    private boolean isBitmap(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
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
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
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
                // No-op
            }
            
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No-op
            }
            
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // No-op
            }
        };
    }
}