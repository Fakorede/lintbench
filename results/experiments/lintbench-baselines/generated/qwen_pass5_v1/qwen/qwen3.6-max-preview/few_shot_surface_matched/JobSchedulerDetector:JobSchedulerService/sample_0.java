package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
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

    private final Map<String, Location> mServices = new HashMap<>();

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo$Builder");
    }

    @Override
    public void visitConstructor(JavaContext context, UCallExpression node, PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression serviceArg = args.get(1);
        if (serviceArg instanceof UCallExpression) {
            UCallExpression cnCall = (UCallExpression) serviceArg;
            List<UExpression> cnArgs = cnCall.getValueArguments();
            if (cnArgs.size() == 2 && cnArgs.get(1) instanceof UClassLiteralExpression) {
                UClassLiteralExpression classLiteral = (UClassLiteralExpression) cnArgs.get(1);
                PsiType type = classLiteral.getType();
                if (type != null) {
                    String fqn = type.getCanonicalText();
                    PsiClass cls = context.getEvaluator().findClass(fqn);
                    if (cls != null && !context.getEvaluator().extendsClass(cls, "android.app.job.JobService", true)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "JobScheduler service must extend android.app.job.JobService");
                    }
                    mServices.put(fqn, context.getLocation(node));
                }
            }
        }
    }

    @Override
    public void checkPartialResults(Context context) {
        for (Map.Entry<String, Location> entry : mServices.entrySet()) {
            context.report(
                    ISSUE,
                    entry.getValue(),
                    "Service " + entry.getKey() + " must be registered in the manifest with "
                            + "android.permission.BIND_JOB_SERVICE");
        }
        mServices.clear();
    }
}