package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PositionXmlParser;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";

    private static final String KEY_SCHEDULED = "scheduled";

    public static final Issue JOB_SCHEDULER_SERVICE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler problems",
                            "The service used in a `JobInfo` must extend `JobService` and be "
                                    + "registered in the manifest with the permission "
                                    + "`android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://developer.android.com/topic/performance/scheduling.html")
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    @Override
    @Nullable
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        String className = resolveServiceClassName(context, args.get(1));
        if (className == null) {
            return;
        }

        PsiClass psiClass = context.getEvaluator().findClass(className);
        if (psiClass != null && !context.getEvaluator().extendsClass(psiClass, JOB_SERVICE, false)) {
            context.report(
                    JOB_SCHEDULER_SERVICE,
                    node,
                    context.getLocation(node),
                    "The service `" + className + "` used in a JobInfo must extend JobService");
        }

        Detector.PartialResultMap partialResults = context.getPartialResults();
        @SuppressWarnings("unchecked")
        Map<String, List<Location>> scheduled =
                (Map<String, List<Location>>) partialResults.get(KEY_SCHEDULED);
        if (scheduled == null) {
            scheduled = new HashMap<>();
            partialResults.put(KEY_SCHEDULED, scheduled);
        }
        scheduled.computeIfAbsent(className, k -> new ArrayList<>()).add(context.getLocation(node));
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull Detector.PartialResultMap partialResult) {
        Object scheduledObj = partialResult.get(KEY_SCHEDULED);
        if (!(scheduledObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, List<Location>> scheduled = (Map<String, List<Location>>) scheduledObj;
        if (scheduled.isEmpty()) {
            return;
        }

        Map<String, ServiceInfo> manifestServices = parseManifests(context);
        for (Map.Entry<String, List<Location>> entry : scheduled.entrySet()) {
            String className = entry.getKey();
            ServiceInfo info = manifestServices.get(className);
            if (info == null) {
                String message =
                        "The service `"
                                + className
                                + "` used in a JobInfo must be registered in the manifest";
                for (Location location : entry.getValue()) {
                    context.report(JOB_SCHEDULER_SERVICE, null, location, message);
                }
            } else if (!info.hasPermission) {
                String message =
                        "The service `"
                                + className
                                + "` used in a JobInfo must require the permission `"
                                + BIND_JOB_SERVICE
                                + "`";
                for (Location location : entry.getValue()) {
                    context.report(JOB_SCHEDULER_SERVICE, null, location, message);
                }
            }
        }
    }

    @Nullable
    private static String resolveServiceClassName(
            @NonNull JavaContext context, @NonNull UExpression expression) {
        if (expression instanceof UClassLiteralExpression) {
            PsiType type = ((UClassLiteralExpression) expression).getType();
            if (type instanceof PsiClassType) {
                PsiClass cls = ((PsiClassType) type).resolve();
                return cls != null ? cls.getQualifiedName() : null;
            }
        }

        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod psiMethod = call.resolve();
            if (psiMethod != null) {
                PsiClass containingClass = psiMethod.getContainingClass();
                if (containingClass != null
                        && COMPONENT_NAME.equals(containingClass.getQualifiedName())) {
                    List<UExpression> componentArgs = call.getValueArguments();
                    if (componentArgs.size() >= 2) {
                        String name = resolveServiceClassName(context, componentArgs.get(1));
                        if (name != null) {
                            return name;
                        }
                    }
                }
            }
        }

        Object value = new ConstantEvaluator(context).evaluate(expression);
        if (value instanceof String) {
            return (String) value;
        }

        return null;
    }

    @NonNull
    private static Map<String, ServiceInfo> parseManifests(@NonNull Context context) {
        Map<String, ServiceInfo> map = new HashMap<>();
        Project project = context.getProject();
        if (project == null) {
            return map;
        }

        List<java.io.File> manifests = project.getManifestFiles();
        if (manifests == null) {
            return map;
        }

        for (java.io.File file : manifests) {
            if (!file.exists()) {
                continue;
            }
            try {
                org.w3c.dom.Document document = PositionXmlParser.parse(file);
                org.w3c.dom.Element root = document.getDocumentElement();
                if (root == null) {
                    continue;
                }
                String packageName = root.getAttribute("package");
                org.w3c.dom.NodeList services = document.getElementsByTagName("service");
                for (int i = 0; i < services.getLength(); i++) {
                    org.w3c.dom.Element service = (org.w3c.dom.Element) services.item(i);
                    String name = service.getAttribute("android:name");
                    if (name == null || name.isEmpty()) {
                        continue;
                    }
                    String fqcn = getFullyQualifiedClassName(packageName, name);
                    if (fqcn == null) {
                        continue;
                    }
                    boolean hasPermission = BIND_JOB_SERVICE.equals(service.getAttribute("android:permission"));
                    map.put(fqcn, new ServiceInfo(fqcn, hasPermission));
                }
            } catch (Exception ignore) {
                // Ignore malformed manifests.
            }
        }

        return map;
    }

    @Nullable
    private static String getFullyQualifiedClassName(
            @Nullable String packageName, @NonNull String className) {
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (className.indexOf('.') == -1 && packageName != null && !packageName.isEmpty()) {
            return packageName + "." + className;
        }
        return className;
    }

    private static final class ServiceInfo {
        final String name;
        final boolean hasPermission;

        ServiceInfo(String name, boolean hasPermission) {
            this.name = name;
            this.hasPermission = hasPermission;
        }
    }
}