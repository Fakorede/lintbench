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

    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String KEY_SERVICE_CLASS = "serviceClass";
    private static final String KEY_LOCATION_FILE = "locationFile";
    private static final String KEY_LOCATION_START = "locationStart";
    private static final String KEY_LOCATION_END = "locationEnd";

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
        return Collections.singletonList(JOB_INFO_BUILDER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        // JobInfo.Builder(int jobId, ComponentName componentName)
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression componentNameArg = args.get(1);
        PsiType type = componentNameArg.getExpressionType();

        // Try to resolve the class being passed as the component name
        // We look for ComponentName constructor calls within the argument
        String serviceClassName = resolveServiceClassName(context, componentNameArg);
        if (serviceClassName == null) {
            return;
        }

        // Check if the service class extends JobService
        PsiClass serviceClass = context.getEvaluator().findClass(serviceClassName);
        if (serviceClass != null) {
            if (!context.getEvaluator().extendsClass(serviceClass, JOB_SERVICE, false)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(componentNameArg),
                        "Scheduled job class `"
                                + serviceClass.getName()
                                + "` must extend `android.app.job.JobService`");
                return;
            }
        }

        // Store partial result for manifest checking
        if (context.isGlobalAnalysis()) {
            checkManifestForService(context, node, serviceClassName);
        } else {
            LintMap map = context.getPartialResults(ISSUE).map();
            String key = serviceClassName;
            // Store location information for later reporting
            map.put(key + "_file", context.file.getPath());
            com.android.tools.lint.detector.api.Location location = context.getLocation(node);
            map.put(key + "_location", location);
        }
    }

    private String resolveServiceClassName(
            @NonNull JavaContext context, @NonNull UExpression expression) {
        // Try to evaluate the expression to find the class name
        // Look for ComponentName constructor calls
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            List<UExpression> callArgs = call.getValueArguments();
            if (callArgs.size() >= 2) {
                // ComponentName(Context, Class<?>) or ComponentName(String, String)
                UExpression classArg = callArgs.get(1);
                // Try to get the class literal
                Object evaluated = context.getEvaluator().evaluate(classArg);
                if (evaluated instanceof PsiClass) {
                    return ((PsiClass) evaluated).getQualifiedName();
                }
                // Try class literal expression
                if (classArg instanceof org.jetbrains.uast.UClassLiteralExpression) {
                    org.jetbrains.uast.UClassLiteralExpression classLiteral =
                            (org.jetbrains.uast.UClassLiteralExpression) classArg;
                    PsiType classType = classLiteral.getType();
                    if (classType != null) {
                        String typeName = classType.getCanonicalText();
                        // Remove array/generic suffixes if any
                        return typeName;
                    }
                }
                // Try string argument
                if (evaluated instanceof String) {
                    return (String) evaluated;
                }
            }
        }
        return null;
    }

    private void checkManifestForService(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull String serviceClassName) {
        // In global analysis mode, we can check the manifest directly
        // This is a simplified check - in practice we'd need to check the merged manifest
        // For now, report issues that we can detect
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // Merge partial results and check manifest
        // In partial analysis, we collect service class names and then
        // verify them against the manifest in this phase
        LintMap map = partialResults.map();
        if (map.isEmpty()) {
            return;
        }

        // Check each recorded service class against the manifest
        for (String key : map) {
            if (key.endsWith("_file")) {
                continue;
            }
            if (key.endsWith("_location")) {
                continue;
            }

            String serviceClassName = key;
            com.android.tools.lint.detector.api.Location location =
                    map.getLocation(key + "_location");

            if (location == null) {
                continue;
            }

            // Check if service is in the manifest with correct permission
            checkServiceInManifest(context, location, serviceClassName);
        }
    }

    private void checkServiceInManifest(
            @NonNull Context context,
            @NonNull com.android.tools.lint.detector.api.Location location,
            @NonNull String serviceClassName) {
        // Check the manifest for this service registration
        com.android.tools.lint.client.api.LintClient client = context.getClient();

        // Try to find the merged manifest
        com.android.tools.lint.detector.api.Project project = context.getProject();
        org.w3c.dom.Document mergedManifest = project.getMergedManifest();
        if (mergedManifest == null) {
            return;
        }

        org.w3c.dom.NodeList services =
                mergedManifest.getElementsByTagName("service");
        boolean found = false;
        boolean hasPermission = false;

        String simpleClassName = serviceClassName;
        // Get just the class name for comparison
        String packageName = project.getPackage();

        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Node serviceNode = services.item(i);
            if (serviceNode instanceof org.w3c.dom.Element) {
                org.w3c.dom.Element serviceElement = (org.w3c.dom.Element) serviceNode;
                String name =
                        serviceElement.getAttributeNS(
                                "http://schemas.android.com/apk/res/android", "name");
                if (name == null || name.isEmpty()) {
                    continue;
                }

                // Normalize the name
                String fullName = name;
                if (name.startsWith(".")) {
                    fullName = packageName + name;
                } else if (!name.contains(".") && packageName != null) {
                    fullName = packageName + "." + name;
                }

                if (fullName.equals(serviceClassName) || name.equals(serviceClassName)) {
                    found = true;
                    String permission =
                            serviceElement.getAttributeNS(
                                    "http://schemas.android.com/apk/res/android", "permission");
                    if (BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                        hasPermission = true;
                    }
                    break;
                }
            }
        }

        if (!found) {
            context.report(
                    ISSUE,
                    location,
                    "Did not find a service entry for `"
                            + serviceClassName
                            + "` in the manifest");
        } else if (!hasPermission) {
            context.report(
                    ISSUE,
                    location,
                    "The service `"
                            + serviceClassName
                            + "` requires the permission `"
                            + BIND_JOB_SERVICE_PERMISSION
                            + "`, add `android:permission=\""
                            + BIND_JOB_SERVICE_PERMISSION
                            + "\"` to the `<service>` tag");
        }
    }
}