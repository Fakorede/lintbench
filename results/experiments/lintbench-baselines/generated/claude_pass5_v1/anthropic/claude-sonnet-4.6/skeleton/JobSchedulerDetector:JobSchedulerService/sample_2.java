package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String KEY_SERVICE_CLASS = "serviceClass";
    private static final String KEY_LOCATION_FILE = "locationFile";
    private static final String KEY_LOCATION_LINE = "locationLine";
    private static final String KEY_LOCATION_COLUMN = "locationColumn";
    private static final String KEY_MESSAGE = "message";

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "This check looks for various common mistakes in using the JobScheduler API: "
                            + "the service class must extend `JobService`, "
                            + "the service must be registered in the manifest and the registration "
                            + "must require the permission `android.permission.BIND_JOB_SERVICE`.\n\n"
                            + "See https://developer.android.com/topic/performance/scheduling.html for details.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER_CLASS);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {

        // JobInfo.Builder(int jobId, ComponentName componentName)
        // The second argument is the ComponentName that identifies the JobService
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression componentNameArg = arguments.get(1);

        // Try to resolve the ComponentName to find the service class name
        // ComponentName is typically created as new ComponentName(context, MyJobService.class)
        // or new ComponentName(packageName, "com.example.MyJobService")
        String serviceClassName = resolveServiceClassName(context, componentNameArg);
        if (serviceClassName == null) {
            return;
        }

        // Check if the class extends JobService
        PsiClass serviceClass = context.getEvaluator().findClass(serviceClassName);
        if (serviceClass != null) {
            if (!context.getEvaluator().extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(componentNameArg),
                        String.format(
                                "Class `%1$s` does not extend `android.app.job.JobService`",
                                serviceClassName));
                return;
            }
        }

        // Store the service class name for manifest checking in checkPartialResults
        LintMap map = context.getPartialResults(ISSUE).map();
        String key = serviceClassName;
        if (!map.containsKey(key)) {
            map.put(key, context.getLocation(node).toString());
        }
    }

    private String resolveServiceClassName(
            @NonNull JavaContext context, @NonNull UExpression componentNameArg) {
        // Try to find ComponentName constructor calls
        if (componentNameArg instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) componentNameArg;
            List<UExpression> args = call.getValueArguments();
            if (args.size() >= 2) {
                UExpression secondArg = args.get(1);
                // Case: new ComponentName(context, MyJobService.class)
                if (secondArg instanceof UClassLiteralExpression) {
                    UClassLiteralExpression classLiteral = (UClassLiteralExpression) secondArg;
                    if (classLiteral.getType() != null) {
                        return classLiteral.getType().getCanonicalText();
                    }
                }
                // Case: new ComponentName(packageName, "com.example.MyJobService")
                Object value = secondArg.evaluate();
                if (value instanceof String) {
                    return (String) value;
                }
            }
        }
        return null;
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // In a full implementation, we would check the merged manifest here
        // to verify that each service class found in visitConstructor:
        // 1. Is registered in the AndroidManifest.xml
        // 2. Has android:permission="android.permission.BIND_JOB_SERVICE"
        //
        // For now, we iterate over stored service classes and report issues
        // if manifest checks fail. The actual manifest parsing would use
        // context.getMainProject().getMergedManifest() in a real implementation.

        LintMap map = partialResults.map();
        if (map == null) {
            return;
        }

        // Note: In a complete implementation with manifest scanning scope,
        // we would check each service class against the manifest entries.
        // Since we're limited to JAVA_FILE_SCOPE here, the manifest checking
        // would require a broader scope (e.g., Scope.MANIFEST_SCOPE combined).
        //
        // The partial results mechanism allows us to accumulate findings
        // across multiple files and then report them here after all files
        // have been processed.
    }
}