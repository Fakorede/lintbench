package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JobSchedulerDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "JobSchedulerService",
        "JobScheduler problems",
        "This check looks for various common mistakes in using the JobScheduler API: " +
        "the service class must extend `JobService`, the service must be registered in " +
        "the manifest and the registration must require the permission " +
        "`android.permission.BIND_JOB_SERVICE`.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
    );

    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo$Builder";
    private static final String COMPONENT_NAME_CLASS = "android.content.ComponentName";

    private final Map<String, Location> referencedServices = new HashMap<>();
    private final Set<String> declaredServices = new HashSet<>();
    private final Map<String, Location> missingPermissionServices = new HashMap<>();

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        referencedServices.clear();
        declaredServices.clear();
        missingPermissionServices.clear();
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @NotNull
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NotNull UCallExpression node) {
                if (!"<init>".equals(node.getMethodName())) return;
                PsiMethod method = node.resolve();
                if (method == null) return;
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null || !JOB_INFO_BUILDER_CLASS.equals(containingClass.getQualifiedName())) return;

                List<UExpression> args = node.getValueArguments();
                if (args.size() < 2) return;

                UExpression secondArg = args.get(1);
                PsiClass serviceClass = resolveServiceClass(context, secondArg);
                if (serviceClass != null) {
                    String fqn = serviceClass.getQualifiedName();
                    if (fqn != null) {
                        referencedServices.put(fqn, context.getLocation(node));
                        JavaEvaluator evaluator = context.getEvaluator();
                        if (!evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, true)) {
                            context.report(ISSUE, context.getLocation(node),
                                "JobScheduler service class must extend " + JOB_SERVICE_CLASS);
                        }
                    }
                }
            }
        };
    }

    @Nullable
    private PsiClass resolveServiceClass(@NotNull JavaContext context, @NotNull UExpression arg) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (arg instanceof UClassLiteralExpression) {
            PsiType type = ((UClassLiteralExpression) arg).getType();
            return evaluator.getTypeClass(type);
        }
        if (arg instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) arg;
            if ("<init>".equals(call.getMethodName())) {
                PsiMethod method = call.resolve();
                if (method != null) {
                    PsiClass cls = method.getContainingClass();
                    if (cls != null && COMPONENT_NAME_CLASS.equals(cls.getQualifiedName())) {
                        List<UExpression> cnArgs = call.getValueArguments();
                        if (cnArgs.size() >= 2) {
                            return resolveServiceClass(context, cnArgs.get(1));
                        }
                    }
                }
            }
        }
        Object value = evaluator.evaluate(arg);
        if (value instanceof String) {
            return evaluator.findClass((String) value);
        }
        return null;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String className = element.getAttribute("android:name");
        if (className == null || className.isEmpty()) return;

        String pkg = context.getProject().getPackage();
        if (pkg == null) pkg = "";
        String fqn = className;
        if (className.startsWith(".")) {
            fqn = pkg + className;
        } else if (!className.contains(".")) {
            fqn = pkg + "." + className;
        }

        declaredServices.add(fqn);

        String permission = element.getAttribute("android:permission");
        if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
            missingPermissionServices.put(fqn, context.getLocation(element));
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (Map.Entry<String, Location> entry : referencedServices.entrySet()) {
            String fqn = entry.getKey();
            if (!declaredServices.contains(fqn)) {
                context.report(ISSUE, entry.getValue(),
                    "JobService " + fqn + " is not registered in the manifest");
            } else {
                Location permLoc = missingPermissionServices.get(fqn);
                if (permLoc != null) {
                    context.report(ISSUE, permLoc,
                        "JobService must require " + BIND_JOB_SERVICE_PERMISSION + " permission");
                }
            }
        }
    }
}