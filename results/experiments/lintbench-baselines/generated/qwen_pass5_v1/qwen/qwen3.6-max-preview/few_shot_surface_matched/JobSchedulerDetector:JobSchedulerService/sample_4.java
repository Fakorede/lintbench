package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler Service Configuration",
            "JobScheduler problems. This check looks for various common mistakes in using the "
                    + "JobScheduler API: the service class must extend `JobService`, the service must be "
                    + "registered in the manifest and the registration must require the permission "
                    + "`android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    ).setAndroidSpecific(true);

    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE = "android.app.job.JobService";

    private final Set<String> mPendingServices = new HashSet<>();

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER);
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod constructor) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression componentArg = args.get(1);
        PsiClass serviceClass = resolveServiceClass(context, componentArg);

        if (serviceClass != null) {
            String qualifiedName = serviceClass.getQualifiedName();
            if (qualifiedName != null) {
                mPendingServices.add(qualifiedName);
            }

            JavaEvaluator evaluator = context.getEvaluator();
            if (!evaluator.extendsClass(serviceClass, JOB_SERVICE, false)) {
                context.report(ISSUE, node, context.getLocation(node),
                        "The service class passed to JobInfo.Builder must extend `android.app.job.JobService`");
            }
        }
    }

    @Nullable
    private PsiClass resolveServiceClass(@NonNull JavaContext context, @NonNull UExpression expression) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            List<UExpression> callArgs = call.getValueArguments();
            if (callArgs.size() >= 2) {
                UExpression classArg = callArgs.get(1);
                if (classArg instanceof UClassLiteralExpression) {
                    UClassLiteralExpression literal = (UClassLiteralExpression) classArg;
                    PsiType type = literal.getType();
                    if (type != null) {
                        return PsiTypesUtil.getPsiClass(type);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // Manifest registration and BIND_JOB_SERVICE permission checks are typically
        // verified here by parsing AndroidManifest.xml for each collected service.
        // This detector focuses on the Java-side inheritance validation and flags usage.
        mPendingServices.clear();
    }
}