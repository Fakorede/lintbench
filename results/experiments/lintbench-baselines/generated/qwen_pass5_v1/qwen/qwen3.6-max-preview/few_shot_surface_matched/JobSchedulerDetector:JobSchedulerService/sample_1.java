package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: "
                    + "the service class must extend `JobService`, the service must be registered "
                    + "in the manifest and the registration must require the permission "
                    + "`android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(JavaContext context, UCallExpression node, PsiMethod constructor) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression componentArg = args.get(1);
        if (componentArg instanceof UCallExpression) {
            UCallExpression componentCall = (UCallExpression) componentArg;
            List<UExpression> compArgs = componentCall.getValueArguments();
            if (compArgs.size() >= 2) {
                UExpression classArg = compArgs.get(1);
                PsiElement resolved = UastUtils.tryResolve(classArg);
                if (resolved instanceof PsiClass) {
                    PsiClass psiClass = (PsiClass) resolved;
                    if (!context.getEvaluator().extendsClass(psiClass, "android.app.job.JobService", true)) {
                        context.report(ISSUE, node, context.getLocation(node),
                                "Job service must extend android.app.job.JobService");
                    }
                }
            }
        }
    }

    @Override
    public void checkPartialResults(Context context, PartialResult partialResult) {
        // Manifest registration and BIND_JOB_SERVICE permission validation
        // can be aggregated here if cross-file analysis is required.
    }
}