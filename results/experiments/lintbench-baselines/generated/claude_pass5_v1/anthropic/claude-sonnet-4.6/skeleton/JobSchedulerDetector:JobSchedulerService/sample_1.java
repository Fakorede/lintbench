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
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String JOB_SCHEDULER_CLASS = "android.app.job.JobScheduler";
    private static final String JOB_INFO_CLASS = "android.app.job.JobInfo";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String KEY_SERVICE_CLASS = "serviceClass";
    private static final String KEY_LOCATION = "location";
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
                            + "must require the permission `android.permission.BIND_JOB_SERVICE`.",
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
        // We want to check the ComponentName argument to find the service class
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        // The second argument is the ComponentName
        UExpression componentNameArg = arguments.get(1);
        PsiType type = componentNameArg.getExpressionType();
        if (type == null) {
            return;
        }

        // Try to resolve the ComponentName to find the actual service class name
        // We look for ComponentName constructor calls or existing references
        String serviceClassName = getServiceClassName(context, componentNameArg);
        if (serviceClassName == null) {
            return;
        }

        // Check if the service class extends JobService
        PsiClass serviceClass = context.getEvaluator().findClass(serviceClassName);
        if (serviceClass != null) {
            if (!context.getEvaluator().extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
                String message =
                        "`"
                                + serviceClassName
                                + "` does not extend `android.app.job.JobService`";
                context.report(ISSUE, node, context.getLocation(componentNameArg), message);
            }
        }

        // Store info for manifest check in partial results
        LintMap map = context.getPartialResults(ISSUE).map();
        String locationKey = KEY_LOCATION + "_" + serviceClassName;
        if (!map.containsKey(locationKey)) {
            map.put(KEY_SERVICE_CLASS + "_" + serviceClassName, serviceClassName);
            map.put(locationKey, context.getLocation(componentNameArg));
        }
    }

    private String getServiceClassName(
            @NonNull JavaContext context, @NonNull UExpression componentNameArg) {
        // Try to evaluate the expression as a ComponentName constructor
        // ComponentName(Context, Class<?>) or ComponentName(String, String)
        if (componentNameArg instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) componentNameArg;
            List<UExpression> args = call.getValueArguments();
            if (args.size() == 2) {
                UExpression classArg = args.get(1);
                // ComponentName(Context, Class<?>)
                PsiType classType = classArg.getExpressionType();
                if (classType != null) {
                    String typeText = classType.getCanonicalText();
                    if (typeText.startsWith("java.lang.Class")) {
                        // Try to get the class name from the expression
                        Object value = context.getEvaluator().evaluate(classArg);
                        if (value instanceof PsiClass) {
                            return ((PsiClass) value).getQualifiedName();
                        }
                        // Try parsing something like MyService.class
                        String exprText = classArg.asSourceString();
                        if (exprText.endsWith(".class")) {
                            String className = exprText.substring(0, exprText.length() - 6).trim();
                            // Try to resolve the class
                            PsiClass resolved = context.getEvaluator().findClass(className);
                            if (resolved != null) {
                                return resolved.getQualifiedName();
                            }
                            // Try with current package
                            String pkg = context.getEvaluator().getPackage(
                                    context.getUastFile() != null
                                            ? context.getUastFile()
                                            : null);
                            if (pkg != null) {
                                resolved = context.getEvaluator().findClass(pkg + "." + className);
                                if (resolved != null) {
                                    return resolved.getQualifiedName();
                                }
                            }
                            return className;
                        }
                    } else if (typeText.equals("java.lang.String")) {
                        // ComponentName(String packageName, String className)
                        Object value = context.getEvaluator().evaluate(classArg);
                        if (value instanceof String) {
                            return (String) value;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // Check manifest for service registrations with the required permission
        LintMap map = partialResults.map();
        for (String key : map) {
            if (key.startsWith(KEY_SERVICE_CLASS + "_")) {
                String serviceClassName = map.getString(key, null);
                if (serviceClassName == null) {
                    continue;
                }

                String locationKey = KEY_LOCATION + "_" + serviceClassName;
                com.android.tools.lint.detector.api.Location location =
                        map.getLocation(locationKey);

                // Check the manifest
                checkManifestForService(context, serviceClassName, location);
            }
        }
    }

    private void checkManifestForService(
            @NonNull Context context,
            @NonNull String serviceClassName,
            com.android.tools.lint.detector.api.Location location) {
        // Use the project's merged manifest to check for the service registration
        com.android.tools.lint.detector.api.Project project = context.getProject();
        if (project == null) {
            return;
        }

        com.android.tools.lint.detector.api.XmlContext xmlContext = null;

        // Try to find the merged manifest
        org.w3c.dom.Document mergedManifest = project.getMergedManifest();
        if (mergedManifest == null) {
            return;
        }

        org.w3c.dom.Element application = null;
        org.w3c.dom.NodeList apps =
                mergedManifest.getElementsByTagName("application");
        if (apps.getLength() > 0) {
            application = (org.w3c.dom.Element) apps.item(0);
        }

        if (application == null) {
            if (location != null) {
                context.report(
                        ISSUE,
                        location,
                        "Did not find a `<service>` declaration for `"
                                + serviceClassName
                                + "` in the manifest");
            }
            return;
        }

        // Look for service elements
        org.w3c.dom.NodeList services = application.getElementsByTagName("service");
        boolean found = false;
        boolean hasPermission = false;

        // Get simple class name and full name for matching
        String simpleName =
                serviceClassName.contains(".")
                        ? serviceClassName.substring(serviceClassName.lastIndexOf('.') + 1)
                        : serviceClassName;

        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Element service = (org.w3c.dom.Element) services.item(i);
            String name = service.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                name = service.getAttribute("android:name");
            }

            if (name != null
                    && (name.equals(serviceClassName)
                            || name.equals("." + simpleName)
                            || name.endsWith("." + simpleName)
                            || name.equals(simpleName))) {
                found = true;

                String permission = service.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "permission");
                if (permission == null || permission.isEmpty()) {
                    permission = service.getAttribute("android:permission");
                }

                if (BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                    hasPermission = true;
                }
                break;
            }
        }

        if (!found) {
            if (location != null) {
                context.report(
                        ISSUE,
                        location,
                        "Did not find a `<service>` declaration for `"
                                + serviceClassName
                                + "` in the manifest");
            }
        } else if (!hasPermission) {
            if (location != null) {
                context.report(
                        ISSUE,
                        location,
                        "The service `"
                                + serviceClassName
                                + "` requires the permission `"
                                + BIND_JOB_SERVICE_PERMISSION
                                + "` to be set in the manifest");
            }
        }
    }
}