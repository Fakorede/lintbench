package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_MIXED_NINE_PATCH =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`,"
                            + " the image file and the nine-patch file will both map to the same"
                            + " drawable resource, `@drawable/file`, which is probably not what was"
                            + " intended. Only one of the two files can be used for the same"
                            + " drawable resource name.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        String name = file.getName();
        return name.endsWith(".png")
                || name.endsWith(".9.png")
                || name.equals("drawable")
                || name.startsWith("drawable-");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No state required; per-project scanning happens in afterCheckEachProject.
    }

    @Override
    public void afterCheckEachProject(Context context) {
        java.util.List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File resDir : resourceFolders) {
            if (!resDir.isDirectory()) {
                continue;
            }
            java.io.File[] children = resDir.listFiles();
            if (children == null) {
                continue;
            }
            for (java.io.File dir : children) {
                if (!dir.isDirectory() || !dir.getName().startsWith("drawable")) {
                    continue;
                }
                java.util.Map<String, java.util.List<java.io.File>> plain =
                        new java.util.HashMap<>();
                java.util.Map<String, java.util.List<java.io.File>> nine =
                        new java.util.HashMap<>();
                java.io.File[] files = dir.listFiles();
                if (files == null) {
                    continue;
                }
                for (java.io.File f : files) {
                    if (!f.isFile()) {
                        continue;
                    }
                    String name = f.getName();
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    if (lower.endsWith(".9.png")) {
                        String base = name.substring(0, name.length() - ".9.png".length());
                        addFile(nine, base, f);
                    } else if (lower.endsWith(".png")) {
                        String base = name.substring(0, name.length() - ".png".length());
                        addFile(plain, base, f);
                    }
                }
                for (java.util.Map.Entry<String, java.util.List<java.io.File>> entry :
                        plain.entrySet()) {
                    String base = entry.getKey();
                    java.util.List<java.io.File> nineFiles = nine.get(base);
                    if (nineFiles != null && !nineFiles.isEmpty()) {
                        reportClash(context, base, entry.getValue(), nineFiles);
                    }
                }
            }
        }
    }

    private void addFile(
            java.util.Map<String, java.util.List<java.io.File>> map, String key, java.io.File file) {
        java.util.List<java.io.File> list = map.get(key);
        if (list == null) {
            list = new java.util.ArrayList<>();
            map.put(key, list);
        }
        list.add(file);
    }

    private void reportClash(
            Context context,
            String base,
            java.util.List<java.io.File> plain,
            java.util.List<java.io.File> nine) {
        String message =
                "The drawable '"
                        + base
                        + "' has both a `.png` and a `.9.png` file; they both map to"
                        + " `@drawable/"
                        + base
                        + "` and only one will be used.";
        for (java.io.File f : plain) {
            context.report(ICON_MIXED_NINE_PATCH, (Object) null, Location.create(f), message);
        }
        for (java.io.File f : nine) {
            context.report(ICON_MIXED_NINE_PATCH, (Object) null, Location.create(f), message);
        }
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return incident.getIssue() == ICON_MIXED_NINE_PATCH;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        // Not used for this issue.
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                // Not used for this issue.
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                // Not used for this issue.
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                // Not used for this issue.
            }
        };
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
                UCallExpression.class, UMethod.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public void visitMethod(UMethod node) {
        // Not used for this issue.
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Not used for this issue.
    }
}