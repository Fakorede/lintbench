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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
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

    private static final String JOB_SCHEDULER_CLASS = "android.app.job.JobScheduler";
    private static final String JOB_INFO_CLASS = "android.app.job.JobInfo";
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String SCHEDULE_METHOD = "schedule";
    private static final String BUILD_METHOD = "build";
    private static final String SET_COMPONENT_METHOD = "setComponent";

    // Map from service class name to the location of the schedule() call
    private final Map<String, UCallExpression> mScheduledServices = new HashMap<>();

    // Map from service class name to whether it has BIND_JOB_SERVICE permission in manifest
    private final Map<String, Boolean> mManifestServices = new HashMap<>();

    // Pending issues to report after we have both source and manifest info
    private final Map<String, JavaContext> mPendingContexts = new HashMap<>();

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(SCHEDULE_METHOD);
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull com.intellij.psi.PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();

        // Check if this is JobScheduler.schedule(JobInfo)
        if (!evaluator.isMemberInClass(method, JOB_SCHEDULER_CLASS)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        UExpression jobInfoArg = arguments.get(0);

        // Try to find the JobInfo.Builder that built this JobInfo
        // We need to trace back to find the component class name
        String serviceClassName = findServiceClassName(context, jobInfoArg);
        if (serviceClassName == null) {
            return;
        }

        // Check if the service class extends JobService
        PsiClass serviceClass = evaluator.findClass(serviceClassName);
        if (serviceClass != null) {
            if (!evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
                context.report(ISSUE, call, context.getLocation(call),
                        "Scheduled job class `" + serviceClassName + "` must extend `android.app.job.JobService`");
                return;
            }
        }

        // Store for cross-checking with manifest
        mScheduledServices.put(serviceClassName, call);
        mPendingContexts.put(serviceClassName, context);
    }

    @Nullable
    private String findServiceClassName(@NonNull JavaContext context,
            @NonNull UExpression jobInfoExpression) {
        // Walk the UAST to find the JobInfo.Builder chain and extract the component class
        ServiceClassVisitor visitor = new ServiceClassVisitor(context.getEvaluator());
        jobInfoExpression.accept(visitor);
        return visitor.getServiceClassName();
    }

    // UAST visitor to find the service class name from a JobInfo expression
    private static class ServiceClassVisitor extends org.jetbrains.uast.visitor.AbstractUastVisitor {
        private final JavaEvaluator mEvaluator;
        private String mServiceClassName;

        ServiceClassVisitor(JavaEvaluator evaluator) {
            mEvaluator = evaluator;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            String methodName = node.getMethodName();
            if (SET_COMPONENT_METHOD.equals(methodName)) {
                // setComponent(ComponentName) - try to get the class name from the ComponentName
                List<UExpression> args = node.getValueArguments();
                if (!args.isEmpty()) {
                    String className = extractClassNameFromComponentName(args.get(0));
                    if (className != null) {
                        mServiceClassName = className;
                    }
                }
            }
            return false;
        }

        @Nullable
        private String extractClassNameFromComponentName(@NonNull UExpression expression) {
            // ComponentName constructor: new ComponentName(context, ClassName.class)
            // or new ComponentName(packageName, className)
            if (expression instanceof UCallExpression) {
                UCallExpression callExpr = (UCallExpression) expression;
                List<UExpression> args = callExpr.getValueArguments();
                if (args.size() >= 2) {
                    UExpression secondArg = args.get(1);
                    // Check for ClassName.class
                    if (secondArg instanceof org.jetbrains.uast.UClassLiteralExpression) {
                        org.jetbrains.uast.UClassLiteralExpression classLiteral =
                                (org.jetbrains.uast.UClassLiteralExpression) secondArg;
                        com.intellij.psi.PsiType type = classLiteral.getType();
                        if (type != null) {
                            return type.getCanonicalText();
                        }
                    }
                    // Check for string literal
                    Object value = secondArg.evaluate();
                    if (value instanceof String) {
                        return (String) value;
                    }
                }
            }
            return null;
        }

        @Nullable
        String getServiceClassName() {
            return mServiceClassName;
        }
    }

    // XmlScanner methods

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We only care about the manifest
        if (!context.document.getDocumentElement().getTagName().equals("manifest")) {
            return;
        }

        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Resolve the full class name
        String packageName = getManifestPackage(context.document);
        String fullName = resolveClassName(name, packageName);

        String permission = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "permission");

        boolean hasBindPermission = BIND_JOB_SERVICE_PERMISSION.equals(permission);
        mManifestServices.put(fullName, hasBindPermission);

        if (!hasBindPermission) {
            // Check if this service is used with JobScheduler
            // We'll check after both passes, but also store for later
            mManifestServices.put(fullName, false);
        }
    }

    @Nullable
    private String getManifestPackage(@NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            String pkg = root.getAttribute("package");
            if (pkg != null && !pkg.isEmpty()) {
                return pkg;
            }
        }
        return null;
    }

    @NonNull
    private String resolveClassName(@NonNull String name, @Nullable String packageName) {
        if (name.startsWith(".")) {
            if (packageName != null) {
                return packageName + name;
            }
            return name.substring(1);
        }
        if (name.contains(".")) {
            return name;
        }
        if (packageName != null) {
            return packageName + "." + name;
        }
        return name;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Cross-check scheduled services against manifest
        for (Map.Entry<String, UCallExpression> entry : mScheduledServices.entrySet()) {
            String serviceClassName = entry.getKey();
            UCallExpression call = entry.getValue();
            JavaContext javaContext = mPendingContexts.get(serviceClassName);

            if (javaContext == null) {
                continue;
            }

            if (!mManifestServices.containsKey(serviceClassName)) {
                // Service not registered in manifest
                javaContext.report(ISSUE, call, javaContext.getLocation(call),
                        "Service `" + serviceClassName + "` is not registered in the manifest");
            } else if (!Boolean.TRUE.equals(mManifestServices.get(serviceClassName))) {
                // Service registered but missing BIND_JOB_SERVICE permission
                javaContext.report(ISSUE, call, javaContext.getLocation(call),
                        "Service `" + serviceClassName + "` does not require the `" +
                        BIND_JOB_SERVICE_PERMISSION + "` permission");
            }
        }
    }

    @Override
    @Nullable
    public List<String> getApplicableAttributes() {
        return null;
    }
}