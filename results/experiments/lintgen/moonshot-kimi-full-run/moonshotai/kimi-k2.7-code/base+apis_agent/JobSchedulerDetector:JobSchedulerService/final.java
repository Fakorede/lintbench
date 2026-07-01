package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "When using JobScheduler, the service class must extend `JobService`, "
                    + "must be registered in the manifest, and the manifest entry must "
                    + "require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER);
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        UExpression component = args.get(1);
        if (!(component instanceof UCallExpression)) {
            return;
        }
        UCallExpression componentCall = (UCallExpression) component;
        PsiMethod method = componentCall.resolve();
        if (method == null || !method.isConstructor()) {
            return;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null
                || !COMPONENT_NAME.equals(containingClass.getQualifiedName())) {
            return;
        }

        List<UExpression> cnArgs = componentCall.getValueArguments();
        if (cnArgs.size() != 2) {
            return;
        }
        UExpression classArg = cnArgs.get(1);
        if (classArg instanceof UClassLiteralExpression) {
            PsiClass cls = ((UClassLiteralExpression) classArg).getDeclaration();
            if (cls != null) {
                checkJobServiceClass(context, node, cls);
            }
        } else if (classArg instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) classArg).getValue();
            if (value instanceof String) {
                String packageName = getPackageName(context.getMainProject());
                String fqcn = normalizeServiceName(packageName, (String) value);
                checkManifest(context, node, fqcn);
            }
        }
    }

    private static void checkJobServiceClass(@NonNull JavaContext context,
            @NonNull UCallExpression node, @NonNull PsiClass cls) {
        if (!context.getEvaluator().extendsClass(cls, JOB_SERVICE, false)) {
            String fqcn = cls.getQualifiedName();
            String name = fqcn != null ? fqcn : cls.getName();
            report(context, node, "The service " + name
                    + " must extend android.app.job.JobService");
            return;
        }

        String fqcn = cls.getQualifiedName();
        if (fqcn != null) {
            checkManifest(context, node, fqcn);
        }
    }

    private static void checkManifest(@NonNull JavaContext context,
            @NonNull UCallExpression node, @NonNull String fqcn) {
        Project project = context.getMainProject();
        Document manifest = project.getManifestDom();
        if (manifest == null) {
            return;
        }
        Element root = manifest.getDocumentElement();
        if (root == null) {
            return;
        }
        String packageName = root.getAttribute("package");
        NodeList services = root.getElementsByTagName("service");
        boolean found = false;
        for (int i = 0, n = services.getLength(); i < n; i++) {
            Element service = (Element) services.item(i);
            String name = service.getAttributeNS(ANDROID_URI, "name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            String serviceClass = normalizeServiceName(packageName, name);
            if (fqcn.equals(serviceClass)) {
                found = true;
                String permission = service.getAttributeNS(ANDROID_URI, "permission");
                if (!BIND_JOB_SERVICE.equals(permission)) {
                    report(context, node, "The service " + fqcn
                            + " must require android.permission.BIND_JOB_SERVICE");
                }
                break;
            }
        }
        if (!found) {
            report(context, node, "The service " + fqcn
                    + " must be registered in the manifest");
        }
    }

    private static void report(@NonNull JavaContext context, @NonNull UElement node,
            @NonNull String message) {
        Location location = context.getLocation(node);
        context.report(ISSUE, node, location, message);
    }

    @Nullable
    private static String getPackageName(@NonNull Project project) {
        String pkg = project.getPackage();
        if (pkg != null && !pkg.isEmpty()) {
            return pkg;
        }
        Document manifest = project.getManifestDom();
        if (manifest != null) {
            Element root = manifest.getDocumentElement();
            if (root != null) {
                return root.getAttribute("package");
            }
        }
        return null;
    }

    @NonNull
    private static String normalizeServiceName(@Nullable String packageName,
            @NonNull String name) {
        String normalized = name.replace('$', '.');
        if (normalized.startsWith(".")) {
            return (packageName != null ? packageName : "") + normalized;
        } else if (normalized.contains(".")) {
            return normalized;
        } else {
            return (packageName != null ? packageName + "." : "") + normalized;
        }
    }
}