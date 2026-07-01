package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
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

    private final Map<String, Location> referencedServices = new HashMap<>();
    private final Set<String> declaredServices = new HashSet<>();

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        referencedServices.clear();
        declaredServices.clear();
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
                JavaEvaluator evaluator = context.getEvaluator();
                PsiClass serviceClass = resolveServiceClass(evaluator, secondArg);
                if (serviceClass != null) {
                    String fqn = serviceClass.getQualifiedName();
                    if (fqn != null) {
                        referencedServices.put(fqn, context.getLocation(node));
                        if (!evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
                            context.report(ISSUE, node,
                                "JobScheduler service class must extend " + JOB_SERVICE_CLASS);
                        }
                    }
                }
            }
        };
    }

    @Nullable
    private PsiClass resolveServiceClass(@NotNull JavaEvaluator evaluator, @NotNull UExpression arg) {
        PsiType type = arg.getExpressionType();
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiType[] params = classType.getParameters();
            if (params.length > 0) {
                return evaluator.getTypeClass(params[0]);
            }
            return evaluator.getTypeClass(type);
        }
        if (arg instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) arg;
            if ("<init>".equals(call.getMethodName())) {
                PsiMethod method = call.resolve();
                if (method != null) {
                    PsiClass cls = method.getContainingClass();
                    if (cls != null && "android.content.ComponentName".equals(cls.getQualifiedName())) {
                        List<UExpression> cnArgs = call.getValueArguments();
                        if (cnArgs.size() >= 2) {
                            return resolveServiceClass(evaluator, cnArgs.get(1));
                        }
                    }
                }
            }
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

        JavaEvaluator evaluator = context.getEvaluator();
        PsiClass cls = evaluator.findClass(fqn);
        if (cls != null && evaluator.extendsClass(cls, JOB_SERVICE_CLASS, false)) {
            String permission = element.getAttribute("android:permission");
            if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                context.report(ISSUE, element,
                    "JobService must require " + BIND_JOB_SERVICE_PERMISSION + " permission");
            }
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (Map.Entry<String, Location> entry : referencedServices.entrySet()) {
            if (!declaredServices.contains(entry.getKey())) {
                context.report(ISSUE, entry.getValue(),
                    "JobService " + entry.getKey() + " is not registered in the manifest");
            }
        }
    }
}