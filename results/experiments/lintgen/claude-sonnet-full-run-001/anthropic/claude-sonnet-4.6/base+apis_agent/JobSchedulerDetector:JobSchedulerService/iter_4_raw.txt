package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
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
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String COMPONENT_NAME_CLASS = "android.content.ComponentName";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String TAG_SERVICE = "service";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, " +
            "the service must be registered in the manifest and the registration " +
            "must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/topic/performance/scheduling.html");

    // Map from service class name -> location of JobInfo.Builder constructor call
    private final Map<String, Location> mScheduledServices = new HashMap<>();
    // Map from service class name -> JavaContext
    private final Map<String, JavaContext> mScheduledContexts = new HashMap<>();
    // Map from service class name -> call node
    private final Map<String, UCallExpression> mScheduledCalls = new HashMap<>();

    // Map from service class name -> whether it has BIND_JOB_SERVICE permission
    // null means not found in manifest
    private final Map<String, Boolean> mManifestServices = new HashMap<>();

    // Track what we've already reported to avoid duplicates
    private final List<String> mReportedIssues = new ArrayList<>();

    public JobSchedulerDetector() {
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_NS, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Normalize class name
        String packageName = context.getProject().getPackage();
        if (name.startsWith(".")) {
            if (packageName != null) {
                name = packageName + name;
            }
        } else if (!name.contains(".") && packageName != null) {
            name = packageName + "." + name;
        }

        String permission = element.getAttributeNS(ANDROID_NS, "permission");
        boolean hasBindJobServicePermission = BIND_JOB_SERVICE_PERMISSION.equals(permission);
        mManifestServices.put(name, hasBindJobServicePermission);
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER_CLASS);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod constructor) {

        JavaEvaluator evaluator = context.getEvaluator();

        // JobInfo.Builder(int jobId, ComponentName componentName)
        List<UExpression> args = call.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression componentNameArg = args.get(1);

        // Try to resolve the ComponentName to find the service class name
        String serviceClassName = resolveServiceClassName(context, componentNameArg);
        if (serviceClassName == null) {
            return;
        }

        Location location = context.getLocation(call);
        mScheduledServices.put(serviceClassName, location);
        mScheduledContexts.put(serviceClassName, context);
        mScheduledCalls.put(serviceClassName, call);

        // Check 1: Does the service class extend JobService?
        PsiClass serviceClass = evaluator.findClass(serviceClassName);
        if (serviceClass != null) {
            if (!evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
                context.report(ISSUE, call, location,
                        "Scheduled job class `" + serviceClassName +
                        "` must extend `android.app.job.JobService`");
            }
        }
    }

    @Nullable
    private String resolveServiceClassName(
            @NonNull JavaContext context,
            @NonNull UExpression componentNameArg) {

        // The argument might be a ComponentName constructor call inline,
        // or it might be a variable reference. Try to evaluate it as a call first.
        if (componentNameArg instanceof UCallExpression) {
            return resolveFromComponentNameCall(context, (UCallExpression) componentNameArg);
        }

        // Try to resolve through variable references by evaluating
        // Look for ComponentName constructor calls in the expression
        return null;
    }

    @Nullable
    private String resolveFromComponentNameCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression componentNameCall) {

        PsiMethod resolved = componentNameCall.resolve();
        if (resolved == null) {
            return null;
        }

        PsiClass containingClass = resolved.getContainingClass();
        if (containingClass == null) {
            return null;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (!COMPONENT_NAME_CLASS.equals(qualifiedName)) {
            return null;
        }

        List<UExpression> cnArgs = componentNameCall.getValueArguments();
        if (cnArgs.size() < 2) {
            return null;
        }

        UExpression secondArg = cnArgs.get(1);

        // Case 1: ComponentName(Context, Class<?>) — second arg is a class literal
        if (secondArg instanceof UClassLiteralExpression) {
            UClassLiteralExpression classLiteral = (UClassLiteralExpression) secondArg;
            PsiType type = classLiteral.getType();
            if (type instanceof PsiClassType) {
                PsiClass psiClass = ((PsiClassType) type).resolve();
                if (psiClass != null) {
                    return psiClass.getQualifiedName();
                }
            }
            return null;
        }

        // Case 2: ComponentName(String, String) — second arg is a string literal
        Object value = secondArg.evaluate();
        if (value instanceof String) {
            return (String) value;
        }

        return null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mScheduledServices.entrySet()) {
            String serviceClassName = entry.getKey();
            Location location = entry.getValue();
            JavaContext javaContext = mScheduledContexts.get(serviceClassName);
            UCallExpression call = mScheduledCalls.get(serviceClassName);

            if (!mManifestServices.containsKey(serviceClassName)) {
                // Service not registered in manifest at all
                String key = "manifest:" + serviceClassName;
                if (!mReportedIssues.contains(key)) {
                    mReportedIssues.add(key);
                    if (javaContext != null && call != null) {
                        javaContext.report(ISSUE, call, location,
                                "The service `" + serviceClassName +
                                "` is not registered in the manifest");
                    } else {
                        context.report(ISSUE, location,
                                "The service `" + serviceClassName +
                                "` is not registered in the manifest");
                    }
                }
            } else {
                // Service is registered; check permission
                Boolean hasPermission = mManifestServices.get(serviceClassName);
                if (hasPermission != null && !hasPermission) {
                    String key = "permission:" + serviceClassName;
                    if (!mReportedIssues.contains(key)) {
                        mReportedIssues.add(key);
                        if (javaContext != null && call != null) {
                            javaContext.report(ISSUE, call, location,
                                    "The service `" + serviceClassName +
                                    "` requires the permission " +
                                    "`android.permission.BIND_JOB_SERVICE`");
                        } else {
                            context.report(ISSUE, location,
                                    "The service `" + serviceClassName +
                                    "` requires the permission " +
                                    "`android.permission.BIND_JOB_SERVICE`");
                        }
                    }
                }
            }
        }
    }
}