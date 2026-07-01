package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String KEY_MIN_SDK = "minSdk";
    private static final int WEBP_MIN_SDK = 15;
    private static final int WEBP_EXTENDED_MIN_SDK = 18;

    private static final String WEBP_FORMAT = "WEBP";
    private static final String WEBP_LOSSY = "WEBP_LOSSY";
    private static final String WEBP_LOSSLESS = "WEBP_LOSSLESS";

    private static final String BITMAP_COMPRESS_FORMAT_CLASS =
            "android.graphics.Bitmap.CompressFormat";
    private static final String BITMAP_FACTORY_CLASS = "android.graphics.BitmapFactory";
    private static final String BITMAP_CLASS = "android.graphics.Bitmap";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as "
                            + "lossless encoding and transparency, requires Android 4.2.1 "
                            + "(API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    5,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private int minSdk = -1;

    public IconDetector() {}

    // ---- Detector lifecycle ----

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        minSdk = context.getProject().getMinSdk();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        minSdk = -1;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int requiredSdk = map.getInt(KEY_MIN_SDK, WEBP_MIN_SDK);
        int currentMinSdk = context.getProject().getMinSdk();
        return currentMinSdk < requiredSdk;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for WebP references in XML drawable files
        String src = element.getAttribute("android:src");
        if (src != null && src.toLowerCase().endsWith(".webp")) {
            int required = WEBP_MIN_SDK;
            LintMap map = new LintMap();
            map.put(KEY_MIN_SDK, required);
            Incident incident =
                    new Incident(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "WebP requires API level " + required + " (current min is %1$d)",
                            fix().name("Update minSdkVersion to " + required).build());
            context.report(incident, map);
        }
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class,
                UClass.class);
    }

    @Override
    public UastHandler createUastHandler(@NonNull JavaContext context) {
        return new WebpUastHandler(context);
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("compress", "decodeFile", "decodeStream", "decodeByteArray",
                "decodeResource");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Handled in UastHandler
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        // Handled in UastHandler
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this detector
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Handled in UastHandler
    }

    private class WebpUastHandler extends UastHandler {

        private final JavaContext context;

        WebpUastHandler(@NonNull JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            String name = node.getIdentifier();
            if (WEBP_FORMAT.equals(name) || WEBP_LOSSY.equals(name)
                    || WEBP_LOSSLESS.equals(name)) {
                com.intellij.psi.PsiElement resolved = node.resolve();
                if (resolved != null) {
                    String qualifiedName = getQualifiedName(resolved);
                    if (qualifiedName != null
                            && qualifiedName.startsWith(BITMAP_COMPRESS_FORMAT_CLASS)) {
                        int required;
                        String message;
                        if (WEBP_LOSSLESS.equals(name)) {
                            required = WEBP_EXTENDED_MIN_SDK;
                            message =
                                    "WebP lossless encoding requires API level "
                                            + required
                                            + " (current min is %1$d)";
                        } else {
                            required = WEBP_MIN_SDK;
                            message =
                                    "WebP requires API level "
                                            + required
                                            + " (current min is %1$d)";
                        }
                        LintMap map = new LintMap();
                        map.put(KEY_MIN_SDK, required);
                        Incident incident =
                                new Incident(
                                        ISSUE,
                                        node,
                                        context.getLocation(node),
                                        message,
                                        fix().name("Update minSdkVersion to " + required)
                                                .build());
                        context.report(incident, map);
                    }
                }
            }
            return false;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression call) {
            String methodName = call.getMethodName();
            if (methodName == null) {
                return false;
            }

            PsiMethod method = call.resolve();
            if (method == null) {
                return false;
            }

            com.intellij.psi.PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return false;
            }

            String className = containingClass.getQualifiedName();
            if (className == null) {
                return false;
            }

            if (BITMAP_CLASS.equals(className) && "compress".equals(methodName)) {
                List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
                if (!args.isEmpty()) {
                    org.jetbrains.uast.UExpression formatArg = args.get(0);
                    String argText = formatArg.asSourceString();
                    if (argText != null) {
                        if (argText.contains(WEBP_LOSSLESS)) {
                            int required = WEBP_EXTENDED_MIN_SDK;
                            LintMap map = new LintMap();
                            map.put(KEY_MIN_SDK, required);
                            Incident incident =
                                    new Incident(
                                            ISSUE,
                                            call,
                                            context.getLocation(call),
                                            "WebP lossless encoding requires API level "
                                                    + required
                                                    + " (current min is %1$d)",
                                            fix().name("Update minSdkVersion to " + required)
                                                    .build());
                            context.report(incident, map);
                        } else if (argText.contains(WEBP_FORMAT)
                                || argText.contains(WEBP_LOSSY)) {
                            int required = WEBP_MIN_SDK;
                            LintMap map = new LintMap();
                            map.put(KEY_MIN_SDK, required);
                            Incident incident =
                                    new Incident(
                                            ISSUE,
                                            call,
                                            context.getLocation(call),
                                            "WebP requires API level "
                                                    + required
                                                    + " (current min is %1$d)",
                                            fix().name("Update minSdkVersion to " + required)
                                                    .build());
                            context.report(incident, map);
                        }
                    }
                }
            }

            return false;
        }

        @Nullable
        private String getQualifiedName(@NonNull com.intellij.psi.PsiElement element) {
            if (element instanceof com.intellij.psi.PsiField) {
                com.intellij.psi.PsiField field = (com.intellij.psi.PsiField) element;
                com.intellij.psi.PsiClass containingClass = field.getContainingClass();
                if (containingClass != null) {
                    String className = containingClass.getQualifiedName();
                    if (className != null) {
                        return className + "." + field.getName();
                    }
                }
            }
            return null;
        }
    }

    private static abstract class UastHandler extends AbstractUastVisitor {
        // Base class for our UAST visitor
    }
}