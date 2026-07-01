package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler Service Configuration",
            "JobScheduler problems: the service class must extend `JobService`, " +
                    "the service must be registered in the manifest and the registration " +
                    "must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    ).setAndroidSpecific(true);

    private final Map<String, Location> mServiceLocations = new HashMap<>();

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER);
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) return;

        UExpression componentArg = args.get(1);
        String serviceClass = resolveServiceClass(context, componentArg);
        if (serviceClass != null) {
            PsiClass psiClass = context.getEvaluator().findClass(serviceClass);
            if (psiClass != null && !context.getEvaluator().extendsClass(psiClass, JOB_SERVICE, false)) {
                context.report(ISSUE, node, context.getLocation(node),
                        "JobScheduler service must extend `android.app.job.JobService`");
            }
            mServiceLocations.put(serviceClass, context.getLocation(node));
        }
    }

    @Nullable
    private String resolveServiceClass(@NonNull JavaContext context, @NonNull UExpression arg) {
        if (arg instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) arg;
            List<UExpression> cnArgs = call.getValueArguments();
            if (cnArgs.size() >= 2) {
                UExpression classArg = cnArgs.get(1);
                PsiClass psiClass = context.getEvaluator().getTypeClass(context.getEvaluator().getType(classArg));
                if (psiClass != null) return psiClass.getQualifiedName();
                Object value = classArg.evaluate();
                if (value instanceof String) return (String) value;
            }
        }
        return null;
    }

    @Override
    public void checkPartialResults(@NonNull Context context) {
        if (mServiceLocations.isEmpty()) return;

        String manifestContent = readManifest(context);
        boolean hasBindPermission = manifestContent.contains(BIND_JOB_PERMISSION);

        for (Map.Entry<String, Location> entry : mServiceLocations.entrySet()) {
            String serviceClass = entry.getKey();
            Location location = entry.getValue();
            StringBuilder message = new StringBuilder();

            boolean isRegistered = manifestContent.contains(serviceClass);
            if (!isRegistered) {
                message.append("Service must be registered in AndroidManifest.xml. ");
            }
            if (!hasBindPermission) {
                message.append("Registration must require permission `").append(BIND_JOB_PERMISSION).append("`.");
            }

            if (message.length() > 0) {
                context.report(ISSUE, location, message.toString().trim());
            }
        }
    }

    @NonNull
    private String readManifest(@NonNull Context context) {
        File manifest = context.getProject().getManifest();
        if (manifest != null && manifest.exists()) {
            try {
                return new String(Files.readAllBytes(manifest.toPath()));
            } catch (IOException ignored) {}
        }
        return "";
    }
}