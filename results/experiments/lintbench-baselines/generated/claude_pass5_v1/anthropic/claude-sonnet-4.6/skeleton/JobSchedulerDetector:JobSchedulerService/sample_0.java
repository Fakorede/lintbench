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
    private static final String KEY_LOCATION = "location";

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
        // We want to check the ComponentName argument to verify the service class
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression componentNameArg = arguments.get(1);
        PsiType type = componentNameArg.getExpressionType();
        if (type == null || !type.getCanonicalText().equals("android.content.ComponentName")) {
            return;
        }

        // Try to resolve the class being referenced in the ComponentName
        // ComponentName is typically constructed as new ComponentName(context, MyJobService.class)
        // or new ComponentName(packageName, "com.example.MyJobService")
        // We look for the class literal form
        String serviceClassName = extractServiceClassName(componentNameArg);
        if (serviceClassName == null) {
            return;
        }

        // Check if the service class extends JobService
        PsiClass serviceClass = context.getEvaluator().findClass(serviceClassName);
        if (serviceClass == null) {
            return;
        }

        if (!context.getEvaluator().extendsClass(serviceClass, JOB_SERVICE, false)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(componentNameArg),
                    "Scheduled job class `"
                            + serviceClassName
                            + "` must extend `android.app.job.JobService`");
            return;
        }

        // Store the service class name for manifest checking in partial results
        if (context.isGlobalAnalysis()) {
            // In global analysis, we can check the manifest directly
            checkManifestForService(context, node, componentNameArg, serviceClassName);
        } else {
            // In partial analysis, store for later
            LintMap map = context.getPartialResults(ISSUE).map();
            String locationKey = KEY_SERVICE_CLASS + "_" + serviceClassName;
            if (!map.containsKey(locationKey)) {
                map.put(locationKey, context.getLocation(componentNameArg));
                map.put(KEY_SERVICE_CLASS + "_name_" + serviceClassName, serviceClassName);
            }
        }
    }

    private void checkManifestForService(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull UExpression locationNode,
            @NonNull String serviceClassName) {
        // This would check the merged manifest for the service registration
        // and BIND_JOB_SERVICE permission. Since we don't have direct manifest
        // access in Java file scope, we report based on what we know.
        // The actual manifest check would be done in checkPartialResults or
        // via a combined scope detector.
    }

    private String extractServiceClassName(@NonNull UExpression componentNameArg) {
        // Try to find the class name from the component name expression
        // This handles cases like new ComponentName(context, MyService.class)
        String text = componentNameArg.asSourceString();
        if (text == null) {
            return null;
        }

        // Resolve the expression to find the actual class
        if (componentNameArg instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) componentNameArg;
            List<UExpression> args = call.getValueArguments();
            if (args.size() >= 2) {
                UExpression classArg = args.get(1);
                String classText = classArg.asSourceString();
                if (classText != null && classText.endsWith(".class")) {
                    String className = classText.substring(0, classText.length() - ".class".length()).trim();
                    // Try to resolve the full class name
                    PsiType type = classArg.getExpressionType();
                    if (type != null) {
                        String typeName = type.getCanonicalText();
                        if (typeName.startsWith("java.lang.Class<")) {
                            // Extract the type parameter
                            int start = typeName.indexOf('<') + 1;
                            int end = typeName.lastIndexOf('>');
                            if (start > 0 && end > start) {
                                return typeName.substring(start, end);
                            }
                        }
                    }
                    return className;
                } else if (args.size() >= 2) {
                    // String form: new ComponentName("com.example", "com.example.MyService")
                    String secondArg = classArg.asSourceString();
                    if (secondArg != null
                            && secondArg.startsWith("\"")
                            && secondArg.endsWith("\"")) {
                        return secondArg.substring(1, secondArg.length() - 1);
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        // In partial analysis mode, check the accumulated results against the manifest
        // For each service class recorded, verify:
        // 1. It's registered in the manifest
        // 2. The registration requires BIND_JOB_SERVICE permission

        LintMap combinedMap = partialResults.mergedMap();
        if (combinedMap == null) {
            return;
        }

        for (String key : combinedMap) {
            if (key.startsWith(KEY_SERVICE_CLASS + "_name_")) {
                String serviceClassName = combinedMap.getString(key, null);
                if (serviceClassName == null) {
                    continue;
                }

                String locationKey = KEY_SERVICE_CLASS + "_" + serviceClassName;
                com.android.tools.lint.detector.api.Location location =
                        combinedMap.getLocation(locationKey);
                if (location == null) {
                    continue;
                }

                // Check manifest for service registration
                // In a real implementation, we would use context.getMainProject()
                // to access the merged manifest and verify service registration
                // and BIND_JOB_SERVICE permission requirement
                checkServiceInManifest(context, serviceClassName, location);
            }
        }
    }

    private void checkServiceInManifest(
            @NonNull Context context,
            @NonNull String serviceClassName,
            @NonNull com.android.tools.lint.detector.api.Location location) {
        // Access the merged manifest to check service registration
        com.android.tools.lint.detector.api.Project project = context.getMainProject();
        if (project == null) {
            return;
        }

        com.android.tools.lint.client.api.XmlParser xmlParser = context.getClient().getXmlParser();
        if (xmlParser == null) {
            return;
        }

        // Check if the service is declared in the manifest with the correct permission
        // This is a simplified check - in practice we'd parse the merged manifest XML
        // and look for <service android:name="serviceClassName"
        //                      android:permission="android.permission.BIND_JOB_SERVICE"/>

        // For now, we report if we can determine the service is not properly configured
        // The actual manifest XML parsing would be done via the project's merged manifest
        try {
            org.w3c.dom.Document mergedManifest = project.getMergedManifest();
            if (mergedManifest == null) {
                return;
            }

            org.w3c.dom.NodeList services =
                    mergedManifest.getElementsByTagName("service");
            boolean found = false;
            boolean hasPermission = false;

            for (int i = 0; i < services.getLength(); i++) {
                org.w3c.dom.Node service = services.item(i);
                if (service.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }
                org.w3c.dom.Element serviceElement = (org.w3c.dom.Element) service;
                String name = serviceElement.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "name");

                // Normalize the class name (handle relative names starting with '.')
                if (name != null && (name.equals(serviceClassName)
                        || name.endsWith("." + getSimpleName(serviceClassName))
                        || serviceClassName.endsWith(name))) {
                    found = true;
                    String permission = serviceElement.getAttributeNS(
                            "http://schemas.android.com/apk/res/android", "permission");
                    if (BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                        hasPermission = true;
                    }
                    break;
                }
            }

            if (!found) {
                context.report(
                        ISSUE,
                        location,
                        "Service `"
                                + serviceClassName
                                + "` is not registered in the manifest");
            } else if (!hasPermission) {
                context.report(
                        ISSUE,
                        location,
                        "Service `"
                                + serviceClassName
                                + "` must require the `"
                                + BIND_JOB_SERVICE_PERMISSION
                                + "` permission");
            }
        } catch (Exception e) {
            // Ignore manifest parsing errors
        }
    }

    private static String getSimpleName(@NonNull String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            return className.substring(lastDot + 1);
        }
        return className;
    }
}