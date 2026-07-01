package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeNativeCodeLocation",
                    "Native code outside library directory",
                    "Application native code should only be placed in the application's library directory. "
                            + "Loading native code from other locations (such as the assets, res, "
                            + "external storage, or arbitrary file paths) increases the risk that the "
                            + "code will be tampered with after installation. Use System.loadLibrary(...) "
                            + "or Runtime.loadLibrary(...) to load from the application's lib directory.",
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String SYSTEM = "java.lang.System";
    private static final String RUNTIME = "java.lang.Runtime";
    private static final String LOAD = "load";
    private static final String LOAD_LIBRARY = "loadLibrary";

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(LOAD, LOAD_LIBRARY);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        String name = method.getName();
        if (name == null) {
            return;
        }
        if (!LOAD.equals(name) && !LOAD_LIBRARY.equals(name)) {
            return;
        }
        if (!context.getEvaluator().isMemberInClass(method, SYSTEM)
                && !context.getEvaluator().isMemberInClass(method, RUNTIME)) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        UExpression arg = args.get(0);
        String literal = getStringLiteral(arg);

        if (LOAD_LIBRARY.equals(name)) {
            if (literal != null && looksLikePath(literal)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "loadLibrary(...) should be passed a library name, not a path (\""
                                + literal
                                + "\"); loading from outside the application's lib directory may be unsafe");
            }
        } else { // LOAD
            if (literal != null) {
                if (!isWithinLibraryDirectory(literal)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Loading native library from \""
                                    + literal
                                    + "\" which is outside the application's library directory; "
                                    + "use loadLibrary(...) instead");
                }
            } else {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Loading a native library from an arbitrary location; "
                                + "use System.loadLibrary(...) or Runtime.loadLibrary(...) to load "
                                + "from the application's lib directory");
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        checkDirectories(context, context.getProject().getAssetDirectories());
        checkDirectories(context, context.getProject().getResourceDirectories());
    }

    private void checkDirectories(Context context, List<File> dirs) {
        if (dirs == null) {
            return;
        }
        for (File dir : dirs) {
            if (dir == null || !dir.exists()) {
                continue;
            }
            checkDirectory(context, dir);
        }
    }

    private void checkDirectory(Context context, File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                checkDirectory(context, file);
            } else if (isNativeExecutable(file)) {
                Location location = Location.create(file);
                context.report(
                        ISSUE,
                        location,
                        "Native executable \""
                                + file.getName()
                                + "\" should not be placed under "
                                + dir.getName()
                                + "; place it in the application's lib directory instead");
            }
        }
    }

    private String getStringLiteral(UExpression expr) {
        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            if (value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    private boolean looksLikePath(String name) {
        return name.contains("/")
                || name.contains("\\")
                || name.contains("..")
                || name.startsWith(".");
    }

    private boolean isWithinLibraryDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.contains("..")) {
            return false;
        }
        String lower = path.toLowerCase();
        if (lower.startsWith("/system/lib/")
                || lower.startsWith("/vendor/lib/")
                || lower.startsWith("/system/vendor/lib/")) {
            return true;
        }
        return (lower.startsWith("/data/data/") || lower.startsWith("/data/app/"))
                && lower.contains("/lib/");
    }

    private boolean isNativeExecutable(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".so")
                || name.endsWith(".elf")
                || name.endsWith(".dylib")
                || name.endsWith(".dll");
    }
}