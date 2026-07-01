package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String JOB_SCHEDULER_CLASS = "android.app.job.JobScheduler";
    private static final String JOB_INFO_CLASS = "android.app.job.JobInfo";
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String COMPONENT_NAME_CLASS = "android.content.ComponentName";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String KEY_SERVICE_CLASS = "serviceClass";
    private static final String KEY_LOCATION_FILE = "locationFile";
    private static final String KEY_LOCATION_START = "locationStart";
    private static final String KEY_LOCATION_END = "locationEnd";
    private static final String KEY_MESSAGE = "message";

    private static final Implementation IMPLEMENTATION_JAVA =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final Implementation IMPLEMENTATION_MANIFEST =
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler problems",
                            "This check looks for various common mistakes in using the "
                                    + " JobScheduler API: the service class must extend `JobService`, "
                                    + " the service must be registered in the manifest and the registration "
                                    + " must require the permission `android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            7,
                            Severity.WARNING,
                            IMPLEMENTATION_MANIFEST)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://developer.android.com/topic/performance/scheduling.html");

    public JobSchedulerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER_CLASS);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod constructor) {

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression componentNameArg = arguments.get(1);

        // Try to resolve the class reference inside the ComponentName constructor
        String serviceClassName = resolveServiceClassName(componentNameArg);
        if (serviceClassName == null) {
            return;
        }

        // Check if the resolved class extends JobService
        PsiClass serviceClass = resolveClass(context, serviceClassName);
        if (serviceClass != null && !extendsJobService(context, serviceClass)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(componentNameArg),
                    "Scheduled service `"
                            + serviceClassName
                            + "` does not extend `android.app.job.JobService`");
        }

        // Store partial result for manifest check
        LintMap map = context.getPartialResults(ISSUE).map();
        String key = "service_" + serviceClassName.replace('.', '_');
        if (!map.containsKey(key)) {
            map.put(key, serviceClassName);
        }
    }

    @Nullable
    private String resolveServiceClassName(@NonNull UExpression componentNameArg) {
        // The ComponentName is typically constructed as new ComponentName(context, MyService.class)
        // or new ComponentName(packageName, "com.example.MyService")
        // We look into the call expression arguments if componentNameArg is itself a constructor call
        if (componentNameArg instanceof UCallExpression) {
            UCallExpression componentCall = (UCallExpression) componentNameArg;
            PsiMethod method = componentCall.resolve();
            if (method == null) {
                return null;
            }
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null
                    || !COMPONENT_NAME_CLASS.equals(containingClass.getQualifiedName())) {
                return null;
            }
            List<UExpression> componentArgs = componentCall.getValueArguments();
            if (componentArgs.size() < 2) {
                return null;
            }
            UExpression classArg = componentArgs.get(1);
            return extractClassName(classArg);
        }
        return null;
    }

    @Nullable
    private String extractClassName(@NonNull UExpression expression) {
        // Handle MyService.class expressions
        String expressionStr = expression.asSourceString();
        if (expressionStr != null && expressionStr.endsWith(".class")) {
            // Strip the .class suffix
            String className = expressionStr.substring(0, expressionStr.length() - ".class".length()).trim();
            // Try to resolve this to a fully qualified name
            PsiClass resolved = null;
            if (expression instanceof org.jetbrains.uast.UClassLiteralExpression) {
                org.jetbrains.uast.UClassLiteralExpression classLiteral =
                        (org.jetbrains.uast.UClassLiteralExpression) expression;
                com.intellij.psi.PsiType type = classLiteral.getType();
                if (type instanceof com.intellij.psi.PsiClassType) {
                    resolved = ((com.intellij.psi.PsiClassType) type).resolve();
                    if (resolved != null) {
                        return resolved.getQualifiedName();
                    }
                }
            }
            return className;
        }
        // Handle string literal "com.example.MyService"
        Object value = expression.evaluate();
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    @Nullable
    private PsiClass resolveClass(@NonNull JavaContext context, @NonNull String className) {
        return context.getEvaluator().findClass(className);
    }

    private boolean extendsJobService(@NonNull JavaContext context, @NonNull PsiClass psiClass) {
        return context.getEvaluator().extendsClass(psiClass, JOB_SERVICE_CLASS, false);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {

        LintMap map = partialResults.map();
        for (String key : map) {
            if (!key.startsWith("service_")) {
                continue;
            }
            String serviceClassName = map.getString(key, null);
            if (serviceClassName == null) {
                continue;
            }

            // Check manifest for service registration and permission
            checkManifestForService(context, serviceClassName);
        }
    }

    private void checkManifestForService(
            @NonNull Context context, @NonNull String serviceClassName) {

        org.w3c.dom.Document mergedManifest = context.getMainProject().getMergedManifest();
        if (mergedManifest == null) {
            return;
        }

        org.w3c.dom.Element application = null;
        org.w3c.dom.NodeList appList =
                mergedManifest.getElementsByTagName("application");
        if (appList.getLength() > 0) {
            application = (org.w3c.dom.Element) appList.item(0);
        }
        if (application == null) {
            return;
        }

        String simpleClassName = serviceClassName;
        int lastDot = serviceClassName.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleClassName = serviceClassName.substring(lastDot + 1);
        }

        org.w3c.dom.NodeList services = application.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Element service = (org.w3c.dom.Element) services.item(i);
            String name = service.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                continue;
            }

            boolean matches = name.equals(serviceClassName)
                    || name.equals("." + simpleClassName)
                    || name.endsWith("." + simpleClassName)
                    || name.equals(simpleClassName);

            if (matches) {
                // Found the service; now check permission
                String permission = service.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "permission");
                if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                    // Report at the project level since we are in checkPartialResults
                    context.report(
                            ISSUE,
                            context.getProject().getDir(),
                            "Service `"
                                    + serviceClassName
                                    + "` is missing the `android:permission=\""
                                    + BIND_JOB_SERVICE_PERMISSION
                                    + "\"` attribute in the manifest");
                }
                return;
            }
        }

        // Service not found in manifest
        context.report(
                ISSUE,
                context.getProject().getDir(),
                "Service `"
                        + serviceClassName
                        + "` is not registered in the manifest");
    }
}