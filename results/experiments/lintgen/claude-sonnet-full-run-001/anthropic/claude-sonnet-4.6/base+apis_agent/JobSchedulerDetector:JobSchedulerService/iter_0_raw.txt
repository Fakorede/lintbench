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
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String JOB_SCHEDULER_CLASS = "android.app.job.JobScheduler";
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String COMPONENT_NAME_CLASS = "android.content.ComponentName";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String TAG_SERVICE = "service";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_PERMISSION = "android:permission";

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
     * Map from service class name to the location of the JobInfo.Builder constructor call
     * that references it. Populated during Java source scanning.
     */
    private final Map<String, Location> mScheduledServices = new HashMap<>();

    /**
     * Map from service class name to whether it has BIND_JOB_SERVICE permission in manifest.
     * Populated during manifest scanning.
     */
    private final Map<String, Boolean> mManifestServices = new HashMap<>();

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
        // If manifest has already been scanned, check now; otherwise deferred to visitElement.
        if (mManifestServices.containsKey(serviceClassName)) {
            Boolean hasPermission = mManifestServices.get(serviceClassName);
            if (hasPermission != null && !hasPermission) {
                context.report(ISSUE, call, location,
                        "The service `" + serviceClassName + "` requires the permission " +
                        "`android.permission.BIND_JOB_SERVICE`");
            }
        } else {
            // Service not found in manifest yet (or not registered at all).
            // We'll report missing manifest registration in afterCheckRootProject.
        }
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
        JavaEvaluator evaluator = context.getEvaluator();

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
        // In UAST, a class literal like MyJobService.class is a UClassLiteralExpression
        if (secondArg instanceof org.jetbrains.uast.UClassLiteralExpression) {
            org.jetbrains.uast.UClassLiteralExpression classLiteral =
                    (org.jetbrains.uast.UClassLiteralExpression) secondArg;
            com.intellij.psi.PsiType type = classLiteral.getType();
            if (type instanceof com.intellij.psi.PsiClassType) {
                PsiClass psiClass = ((com.intellij.psi.PsiClassType) type).resolve();
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
                context.report(ISSUE, entry.getValue(),
                        "The service `" + serviceClassName +
                        "` is not registered in the manifest");
            }
        }
    }
}