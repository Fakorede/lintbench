package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UastScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UClass;

public class JobSchedulerDetector extends Detector implements UastScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerProblem",
            "Common mistakes in using the JobScheduler API",
            "The service class must extend `JobService`, the service must be registered in the manifest and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableSuperClasses() {
        return Collections.singletonList("android.app.job.JobService");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass klass) {
        if (klass.getJavaPsi() instanceof PsiClass && UastUtils.isSubclassOf(klass, "android.app.job.JobService")) {
            PsiElement element = klass.getJavaPsi();
            if (!context.getManifest().hasService(element)) {
                context.report(ISSUE, klass, context.getLocation(klass),
                        "JobService subclass is not registered in the manifest");
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @NonNull
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, PsiMethod method) {
                if ("schedule".equals(method.getName())) {
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null && !UastUtils.isSubclassOf(containingClass, "android.app.job.JobService")) {
                        context.report(ISSUE, node, context.getLocation(node),
                                "Method schedule should be called on a subclass of JobService");
                    }
                }
            }

            @Override
            public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, PsiMethod constructor) {
                if (constructor.getName().equals("<init>")) {
                    PsiClass containingClass = constructor.getContainingClass();
                    if (containingClass != null && !UastUtils.isSubclassOf(containingClass, "android.app.job.JobService")) {
                        context.report(ISSUE, node, context.getLocation(node),
                                "Constructor should be called on a subclass of JobService");
                    }
                }
            }
        };
    }

}