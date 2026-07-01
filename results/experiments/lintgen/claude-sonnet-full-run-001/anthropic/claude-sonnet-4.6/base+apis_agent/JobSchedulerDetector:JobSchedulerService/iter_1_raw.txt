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

    /**
     * Map from service class name to the location of the JobInfo.Builder constructor call.
     * Populated during Java source scanning.
     */
    private final Map<String, Location> mScheduledServices = new HashMap<>();

    /**
     * Map from service class name to whether it has BIND_JOB_SERVICE permission in manifest.
     * Populated during manifest scanning.
     */
    private final Map<String, Boolean> mManifestServices = new HashMap<>();

    /**
     * Map from service class name to the JavaContext, for deferred reporting.
     */
    private final Map<String, JavaContext> mScheduledContexts = new HashMap<>();

    /**
     * Map from service class name to the call node, for deferred reporting.
     */
    private final Map<String, UCallExpression> mScheduledCalls = new HashMap<>();

    public JobSchedulerDetector() {
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Normalize class name (handle relative names starting with '.')
        String packageName = context.getProject().getPackage();
        if (name.startsWith(".")) {
            if (packageName != null) {
                name = packageName + name;
            }
        } else if (!name.contains(".") && packageName != null) {
            name = packageName + "." + name;
        }

        String permission = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "permission");

        boolean hasBindJobServicePermission =
                BIND_JOB_SERVICE_PERMISSION.equals(permission);

        mManifestServices.put(name, hasBindJobServicePermission);

        // If we already saw this service being scheduled (Java scanned first),
        // check the permission now.
        if (mScheduledServices.containsKey(name)) {
            if (!hasBindJobServicePermission) {
                Location location = context.getLocation(element);
                context.report(ISSUE, element, location,
                        "The service `" + name + "` requires the permission " +
                        "`android.permission.BIND_JOB_SERVICE`");
            }
        }
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

        // Check 2 & 3: Is the service registered in the manifest with BIND_JOB_SERVICE?
        // If manifest has already been scanned, check now; otherwise deferred to afterCheckRootProject.
        if (mManifestServices.containsKey(serviceClassName)) {
            Boolean hasPermission = mManifestServices.get(serviceClassName);
            if (hasPermission != null && !hasPermission) {
                context.report(ISSUE, call, location,
                        "The service `" + serviceClassName + "` requires the permission " +
                        "`android.permission.BIND_JOB_SERVICE`");
            }
        }
        // If not in manifest yet, we'll handle it in afterCheckRootProject
    }

    @Nullable
    private String resolveServiceClassName(
            @NonNull JavaContext context,
            @NonNull UExpression componentNameArg) {

        // The ComponentName constructor is typically:
        //   new ComponentName(context, MyJobService.class)
        // or
        //   new ComponentName("com.example", "com.example.MyJobService")
        //
        // We look for a UCallExpression that is a ComponentName constructor.
        if (!(componentNameArg instanceof UCallExpression)) {
            return null;
        }

        UCallExpression componentNameCall = (UCallExpression) componentNameArg;

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

        // Case 1: ComponentName(Context, Class<?>)  — second arg is a class literal
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
        // Report services that were scheduled but never found in the manifest
        for (Map.Entry<String, Location> entry : mScheduledServices.entrySet()) {
            String serviceClassName = entry.getKey();
            if (!mManifestServices.containsKey(serviceClassName)) {
                // Service not registered in manifest at all
                JavaContext javaContext = mScheduledContexts.get(serviceClassName);
                UCallExpression call = mScheduledCalls.get(serviceClassName);
                if (javaContext != null && call != null) {
                    javaContext.report(ISSUE, call, entry.getValue(),
                            "The service `" + serviceClassName +
                            "` is not registered in the manifest");
                } else {
                    context.report(ISSUE, entry.getValue(),
                            "The service `" + serviceClassName +
                            "` is not registered in the manifest");
                }
            } else {
                // Service is registered; check if permission was set correctly
                // (This case is handled in visitElement when manifest is scanned after Java,
                //  but if manifest was scanned before Java, it's handled in visitConstructor.
                //  We need to handle the case where manifest was scanned AFTER Java here.)
                Boolean hasPermission = mManifestServices.get(serviceClassName);
                if (hasPermission != null && !hasPermission) {
                    // Already reported in visitElement if manifest was scanned after Java?
                    // Actually visitElement reports if mScheduledServices already has the entry.
                    // If Java was scanned first, visitConstructor won't find it in mManifestServices yet,
                    // so we need to report here.
                    // But we need to avoid double-reporting.
                    // The logic: visitConstructor reports if mManifestServices already has the entry.
                    // visitElement reports if mScheduledServices already has the entry.
                    // So exactly one of them will report - no double reporting.
                    // This branch handles: Java scanned first, manifest scanned after.
                    // But visitElement already handles that case. So nothing to do here.
                }
            }
        }
    }
}