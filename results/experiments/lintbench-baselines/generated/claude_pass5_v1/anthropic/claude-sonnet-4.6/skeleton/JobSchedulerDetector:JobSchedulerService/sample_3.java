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
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String KEY_SERVICE_CLASS = "serviceClass";
    private static final String KEY_LOCATION_FILE = "locationFile";
    private static final String KEY_LOCATION_LINE = "locationLine";
    private static final String KEY_LOCATION_COLUMN = "locationColumn";

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
        // JobInfo.Builder constructor signature: Builder(int jobId, ComponentName jobService)
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        // The second argument is the ComponentName for the job service
        UExpression componentNameArg = args.get(1);

        // Try to resolve the class name from the ComponentName argument
        // ComponentName can be constructed as new ComponentName(context, MyJobService.class)
        // or new ComponentName(packageName, "com.example.MyJobService")
        String serviceClassName = resolveServiceClassName(context, componentNameArg);

        if (serviceClassName == null) {
            return;
        }

        // Check if the resolved class extends JobService
        PsiClass serviceClass = context.getEvaluator().findClass(serviceClassName);
        if (serviceClass == null) {
            // Can't resolve the class, skip
            return;
        }

        if (!context.getEvaluator().extendsClass(serviceClass, JOB_SERVICE, false)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(componentNameArg),
                    "Scheduled job class `"
                            + serviceClassName
                            + "` does not extend `android.app.job.JobService`");
            return;
        }

        // Store partial results for manifest checking in checkPartialResults
        if (context.isGlobalAnalysis()) {
            // In global analysis mode, we can check the manifest directly
            checkManifest(context, node, componentNameArg, serviceClassName);
        } else {
            // In partial analysis mode, store the info for later
            LintMap map = context.getPartialResults(ISSUE).map();
            String locationKey = serviceClassName + "_location";
            map.put(KEY_SERVICE_CLASS + "_" + serviceClassName, serviceClassName);
            // Store location info
            com.android.tools.lint.detector.api.Location location =
                    context.getLocation(componentNameArg);
            map.put(locationKey + "_file", location.getFile().getPath());
        }
    }

    private String resolveServiceClassName(
            @NonNull JavaContext context, @NonNull UExpression componentNameArg) {
        // Try to evaluate the ComponentName construction
        // ComponentName(Context, Class<?>) or ComponentName(String, String)
        if (componentNameArg instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) componentNameArg;
            List<UExpression> callArgs = call.getValueArguments();
            if (callArgs.size() == 2) {
                // ComponentName(Context, Class<?>) - second arg is a class literal
                UExpression classArg = callArgs.get(1);
                // Try to get the class name from a class literal
                Object evaluated = context.getEvaluator().getTypeClass(classArg.getExpressionType());
                if (evaluated instanceof PsiClass) {
                    return ((PsiClass) evaluated).getQualifiedName();
                }
                // Try evaluating as a string (package name form)
                Object value = classArg.evaluate();
                if (value instanceof String) {
                    return (String) value;
                }
            }
        }

        // Try direct class reference
        Object value = componentNameArg.evaluate();
        if (value instanceof String) {
            return (String) value;
        }

        // Try to get the type of the expression
        com.intellij.psi.PsiType type = componentNameArg.getExpressionType();
        if (type != null) {
            PsiClass psiClass = context.getEvaluator().getTypeClass(type);
            if (psiClass != null) {
                String qualifiedName = psiClass.getQualifiedName();
                if (qualifiedName != null && !qualifiedName.equals("android.content.ComponentName")) {
                    return qualifiedName;
                }
            }
        }

        return null;
    }

    private void checkManifest(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull UExpression componentNameArg,
            @NonNull String serviceClassName) {
        // Check if the service is declared in the manifest with the correct permission
        // This is a simplified check - in a real implementation, we'd parse the manifest
        com.android.tools.lint.detector.api.Location location = context.getLocation(componentNameArg);

        // We'll report issues found during partial result checking
        // For global analysis, we check here
        boolean foundInManifest = false;
        boolean hasCorrectPermission = false;

        // Access manifest info through the project
        com.android.tools.lint.detector.api.Project project = context.getProject();
        if (project != null) {
            // Check merged manifest
            org.w3c.dom.Document mergedManifest = project.getMergedManifest();
            if (mergedManifest != null) {
                org.w3c.dom.NodeList services =
                        mergedManifest.getElementsByTagName("service");
                String simpleClassName = serviceClassName;
                for (int i = 0; i < services.getLength(); i++) {
                    org.w3c.dom.Node service = services.item(i);
                    org.w3c.dom.NamedNodeMap attrs = service.getAttributes();
                    if (attrs == null) continue;

                    org.w3c.dom.Node nameAttr = attrs.getNamedItemNS(
                            "http://schemas.android.com/apk/res/android", "name");
                    if (nameAttr == null) continue;

                    String name = nameAttr.getNodeValue();
                    // Handle both fully qualified and short names
                    String packageName = project.getPackage();
                    if (name != null) {
                        String fullName = name;
                        if (name.startsWith(".") && packageName != null) {
                            fullName = packageName + name;
                        } else if (!name.contains(".") && packageName != null) {
                            fullName = packageName + "." + name;
                        }

                        if (fullName.equals(simpleClassName) || name.equals(simpleClassName)) {
                            foundInManifest = true;

                            org.w3c.dom.Node permAttr = attrs.getNamedItemNS(
                                    "http://schemas.android.com/apk/res/android", "permission");
                            if (permAttr != null
                                    && BIND_JOB_SERVICE_PERMISSION.equals(
                                            permAttr.getNodeValue())) {
                                hasCorrectPermission = true;
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (!foundInManifest) {
            context.report(
                    ISSUE,
                    node,
                    location,
                    "Job service `"
                            + serviceClassName
                            + "` is not registered in the manifest");
        } else if (!hasCorrectPermission) {
            context.report(
                    ISSUE,
                    node,
                    location,
                    "Job service `"
                            + serviceClassName
                            + "` does not require the `"
                            + BIND_JOB_SERVICE_PERMISSION
                            + "` permission");
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        LintMap map = partialResults.merge();
        if (map == null || map.isEmpty()) {
            return;
        }

        // Iterate through stored service class names and check manifest
        for (String key : map) {
            if (key.startsWith(KEY_SERVICE_CLASS + "_")) {
                String serviceClassName = map.getString(key, null);
                if (serviceClassName == null) continue;

                boolean foundInManifest = false;
                boolean hasCorrectPermission = false;

                com.android.tools.lint.detector.api.Project project = context.getProject();
                if (project != null) {
                    org.w3c.dom.Document mergedManifest = project.getMergedManifest();
                    if (mergedManifest != null) {
                        org.w3c.dom.NodeList services =
                                mergedManifest.getElementsByTagName("service");
                        String packageName = project.getPackage();

                        for (int i = 0; i < services.getLength(); i++) {
                            org.w3c.dom.Node service = services.item(i);
                            org.w3c.dom.NamedNodeMap attrs = service.getAttributes();
                            if (attrs == null) continue;

                            org.w3c.dom.Node nameAttr = attrs.getNamedItemNS(
                                    "http://schemas.android.com/apk/res/android", "name");
                            if (nameAttr == null) continue;

                            String name = nameAttr.getNodeValue();
                            if (name != null) {
                                String fullName = name;
                                if (name.startsWith(".") && packageName != null) {
                                    fullName = packageName + name;
                                } else if (!name.contains(".") && packageName != null) {
                                    fullName = packageName + "." + name;
                                }

                                if (fullName.equals(serviceClassName)
                                        || name.equals(serviceClassName)) {
                                    foundInManifest = true;

                                    org.w3c.dom.Node permAttr = attrs.getNamedItemNS(
                                            "http://schemas.android.com/apk/res/android",
                                            "permission");
                                    if (permAttr != null
                                            && BIND_JOB_SERVICE_PERMISSION.equals(
                                                    permAttr.getNodeValue())) {
                                        hasCorrectPermission = true;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }

                com.android.tools.lint.detector.api.Location location =
                        com.android.tools.lint.detector.api.Location.create(context.file);

                if (!foundInManifest) {
                    context.report(
                            ISSUE,
                            location,
                            "Job service `"
                                    + serviceClassName
                                    + "` is not registered in the manifest");
                } else if (!hasCorrectPermission) {
                    context.report(
                            ISSUE,
                            location,
                            "Job service `"
                                    + serviceClassName
                                    + "` does not require the `"
                                    + BIND_JOB_SERVICE_PERMISSION
                                    + "` permission");
                }
            }
        }
    }
}