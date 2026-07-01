package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
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
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String DRAWABLE_PREFIX = "@drawable/";

    private static final List<String> DRAWABLE_ATTRIBUTES = Arrays.asList(
            "src",
            "background",
            "foreground",
            "icon",
            "logo",
            "drawable",
            "drawableLeft",
            "drawableRight",
            "drawableTop",
            "drawableBottom",
            "drawableStart",
            "drawableEnd",
            "drawableTint",
            "srcCompat",
            "name"
    );

    private final Map<Project, Set<String>> mBitmapDrawables = new HashMap<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        mBitmapDrawables.clear();
        LintDriver driver = context.getDriver();
        for (Project project : driver.getProjects()) {
            Set<String> names = new HashSet<>();
            for (File res : project.getResourceDirectories()) {
                File drawable = new File(res, "drawable");
                if (drawable.isDirectory()) {
                    File[] files = drawable.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile() && isBitmap(file.getName())) {
                                names.add(getBaseName(file.getName()));
                            }
                        }
                    }
                }
            }
            if (!names.isEmpty()) {
                mBitmapDrawables.put(project, names);
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        mBitmapDrawables.remove(context.getProject());
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return false;
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return true;
    }