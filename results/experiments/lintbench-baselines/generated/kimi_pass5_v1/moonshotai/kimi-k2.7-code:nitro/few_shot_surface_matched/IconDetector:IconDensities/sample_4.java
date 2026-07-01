package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintDriver;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_DENSITIES =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the major"
                            + " screen density classes (low, medium, high, extra high). This lint"
                            + " check identifies icons which do not have complete coverage across"
                            + " the densities.\n\nLow density is not really used much anymore, so"
                            + " this check ignores the ldpi density. To force lint to include it,"
                            + " set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`.",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String APP_URI = "http://schemas.android.com/apk/res-auto";

    private final Map<String, List<File>> mIconFiles = new HashMap<>();
    private final Set<String> mReferencedIcons = new HashSet<>();
    private final Set<String> mReported = new HashSet<>();
    private final Set<String> mRequiredDensities = new HashSet<>();
    private boolean mIncludeLdpi;

    @Override
    public void beforeCheckRootProject(Context context) {
        mIconFiles.clear();
        mReferencedIcons.clear();
        mReported.clear();
        mRequiredDensities.clear();

        mIncludeLdpi = Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        mRequiredDensities.add("mdpi");
        mRequiredDensities.add("hdpi");
        mRequiredDensities.add("xhdpi");
        if (mIncludeLdpi) {
            mRequiredDensities.add("ldpi");
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        try {
            for (File resDir : context.getProject().getResourceFolders()) {
                if (!resDir.isDirectory()) {
                    continue;
                }
                File[] drawableDirs = resDir.listFiles();
                if (drawableDirs == null) {
                    continue;
                }
                for (File dir : drawableDirs) {
                    String dirName = dir.getName();
                    if (!dirName.startsWith("drawable") || !dir.isDirectory()) {
                        continue;
                    }
                    File[] files = dir.listFiles();
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        if (file.isDirectory() || !isDrawableFile(file)) {
                            continue;
                        }
                        String baseName = getBaseName(file);
                        mIconFiles.computeIfAbsent(baseName, k -> new ArrayList<>()).add(file);
                    }
                }
            }

            for (Map.Entry<String, List<File>> entry : mIconFiles.entrySet()) {
                String baseName = entry.getKey();
                Set<String> densities = new HashSet<>();
                boolean hasNoDpi = false;
                boolean hasDefault = false;
                for (File file : entry.getValue()) {
                    String density = getDensity(file.getParentFile().getName());
                    if (density == null) {
                        hasDefault = true;
                    } else if ("nodpi".equals(density)) {
                        hasNoDpi = true;
                    } else {
                        densities.add(density);
                    }
                }

                if (hasNoDpi || (densities.isEmpty() && hasDefault)) {
                    continue;
                }

                Set<String> missing = new HashSet<>(mRequiredDensities);
                missing.removeAll(densities);
                if (missing.isEmpty()) {
                    continue;
                }

                String key = context.getProject().getName() + ":" + baseName;
                if (mReported.add(key)) {
                    List<String> sorted = new ArrayList<>(missing);
                    Collections.sort(sorted);
                    File first = entry.getValue().get(0);
                    String message =
                            "Icon is missing density variations: "
                                    + sorted
                                    + ". Provide versions for all major screen densities.";
                    context.report(
                            ICON_DENSITIES,
                            Location.create(first),
                            message,
                            first);
                }
            }
        } finally {
            mIconFiles.clear();
        }
    }

    @Override
    public boolean filterIncident(Incident incident, LintDriver driver, Object cookie) {
        return true;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        String path = file.getPath();
        return path.contains(File.separator + "drawable")
                || path.endsWith(".java")
                || path.endsWith(".kt");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        File file = context.file;
        if (file == null || !file.getPath().contains(File.separator + "drawable")) {
            return;
        }

        String[] attributes = {"src", "drawable", "srcCompat", "icon", "logo"};
        for (String attribute : attributes) {
            String value = element.getAttributeNS(ANDROID_URI, attribute);
            if (value == null || value.isEmpty()) {
                value = element.getAttributeNS(APP_URI, attribute);
            }
            if (value != null && value.startsWith("@drawable/")) {
                mReferencedIcons.add(value.substring("@drawable/".length()));
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                USimpleNameReferenceExpression.class,
                UCallExpression.class,
                UClass.class,
                UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression expression) {
                PsiElement resolved = expression.resolve();
                if (resolved instanceof PsiField) {
                    PsiField field = (PsiField) resolved;
                    PsiClass cls = field.getContainingClass();
                    if (cls != null && "drawable".equals(cls.getName())) {
                        String name = field.getName();
                        if (name != null) {
                            mReferencedIcons.add(name);
                        }
                    }
                }
            }

            @Override
            public void visitCallExpression(UCallExpression node) {}

            @Override
            public void visitClass(UClass node) {}

            @Override
            public void visitMethod(UMethod node) {}
        };
    }

    private static String getDensity(String folderName) {
        if (!folderName.startsWith("drawable")) {
            return null;
        }
        int start = folderName.indexOf('-');
        if (start == -1) {
            return null;
        }
        int end = folderName.indexOf('-', start + 1);
        String token =
                end == -1 ? folderName.substring(start + 1) : folderName.substring(start + 1, end);
        switch (token) {
            case "ldpi":
            case "mdpi":
            case "hdpi":
            case "xhdpi":
            case "xxhdpi":
            case "xxxhdpi":
            case "tvdpi":
            case "nodpi":
                return token;
            default:
                return null;
        }
    }

    private static String getBaseName(File file) {
        String name = file.getName();
        if (name.endsWith(".9.png")) {
            return name.substring(0, name.length() - ".9.png".length());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static boolean isDrawableFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".xml");
    }
}