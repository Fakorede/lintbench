package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "This check looks for various common mistakes in using the JobScheduler API: " +
                    "the service class must extend `JobService`, the service must be registered in " +
                    "the manifest and the registration must require the permission " +
                    "`android.permission.BIND_JOB_SERVICE`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Set<String> mJobServices = new HashSet<>();

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.content.ComponentName");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) return;

        UExpression arg = args.get(1);
        PsiClass cls = context.getEvaluator().getTypeClass(arg.getExpressionType());
        if (cls == null) return;

        String fqn = cls.getQualifiedName();
        if (fqn == null) return;

        if (!context.getEvaluator().extendsClass(cls, "android.app.job.JobService", false)) {
            context.report(ISSUE, node, context.getLocation(node),
                    "JobService class must extend android.app.job.JobService");
        }
        mJobServices.add(fqn);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        if (mJobServices.isEmpty()) return;

        File manifest = context.getProject().getManifest();
        if (manifest == null || !manifest.exists()) return;

        String content = readManifest(manifest);
        if (content == null) return;

        for (String fqn : mJobServices) {
            String shortName = "." + fqn.substring(fqn.lastIndexOf('.') + 1);
            boolean registered = content.contains("android:name=\"" + fqn + "\"") ||
                                 content.contains("android:name=\"" + shortName + "\"");

            if (!registered) {
                context.report(ISSUE, Location.create(manifest),
                        "JobService " + fqn + " must be registered in the manifest");
                continue;
            }

            if (!content.contains("android.permission.BIND_JOB_SERVICE")) {
                context.report(ISSUE, Location.create(manifest),
                        "JobService " + fqn + " must require android.permission.BIND_JOB_SERVICE");
            }
        }
    }

    private String readManifest(File manifest) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(manifest))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }
}