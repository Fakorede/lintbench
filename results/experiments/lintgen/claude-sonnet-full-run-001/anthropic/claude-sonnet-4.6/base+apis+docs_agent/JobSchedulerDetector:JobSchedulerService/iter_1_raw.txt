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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    // Map from service class name -> call expression (for reporting)
    private final Map<String, UCallExpression> mScheduledServices = new HashMap<>();
    // Map from service class name -> JavaContext
    private final Map<String, JavaContext> mPendingContexts = new HashMap<>();
    // Map from service class name -> has BIND_JOB_SERVICE permission
    private final Map<String, Boolean> mManifestServices = new HashMap<>();

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("schedule");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();

        if (!evaluator.isMemberInClass(method, JOB_SCHEDULER_CLASS)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        UExpression jobInfoArg = arguments.get(0);
        String serviceClassName = findServiceClassName(context, jobInfoArg);
        if (serviceClassName == null) {
            return;
        }

        // Check if the service class extends JobService
        PsiClass serviceClass = evaluator.findClass(serviceClassName);
        if (serviceClass != null) {
            if (!evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
                context.report(ISSUE, call, context.getLocation(call),
                        "Scheduled job class `" + serviceClassName +
                        "` must extend `android.app.job.JobService`");
                return;
            }
        }

        mScheduledServices.put(serviceClassName, call);
        mPendingContexts.put(serviceClassName, context);
    }

    @Nullable
    private String findServiceClassName(@NonNull JavaContext context,
            @NonNull UExpression jobInfoExpression) {
        ServiceClassVisitor visitor = new ServiceClassVisitor(context.getEvaluator());
        jobInfoExpression.accept(visitor);
        return visitor.getServiceClassName();
    }

    private static class ServiceClassVisitor extends AbstractUastVisitor {
        private final JavaEvaluator mEvaluator;
        private String mServiceClassName;

        ServiceClassVisitor(JavaEvaluator evaluator) {
            mEvaluator = evaluator;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            String methodName = node.getMethodName();
            if ("setComponent".equals(methodName)) {
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
            if (expression instanceof UCallExpression) {
                UCallExpression callExpr = (UCallExpression) expression;
                List<UExpression> args = callExpr.getValueArguments();
                if (args.size() >= 2) {
                    UExpression secondArg = args.get(1);
                    if (secondArg instanceof UClassLiteralExpression) {
                        UClassLiteralExpression classLiteral = (UClassLiteralExpression) secondArg;
                        PsiType type = classLiteral.getType();
                        if (type != null) {
                            return type.getCanonicalText();
                        }
                    }
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

    // XmlScanner

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_NS, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = getManifestPackage(context.document);
        String fullName = resolveClassName(name, packageName);

        String permission = element.getAttributeNS(ANDROID_NS, "permission");
        boolean hasBindPermission = BIND_JOB_SERVICE_PERMISSION.equals(permission);
        mManifestServices.put(fullName, hasBindPermission);
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
        for (Map.Entry<String, UCallExpression> entry : mScheduledServices.entrySet()) {
            String serviceClassName = entry.getKey();
            UCallExpression call = entry.getValue();
            JavaContext javaContext = mPendingContexts.get(serviceClassName);

            if (javaContext == null) {
                continue;
            }

            Location location = javaContext.getLocation(call);

            if (!mManifestServices.containsKey(serviceClassName)) {
                javaContext.report(ISSUE, call, location,
                        "Service `" + serviceClassName +
                        "` is not registered in the manifest");
            } else if (!Boolean.TRUE.equals(mManifestServices.get(serviceClassName))) {
                javaContext.report(ISSUE, call, location,
                        "Service `" + serviceClassName +
                        "` does not require the `" + BIND_JOB_SERVICE_PERMISSION + "` permission");
            }
        }
    }
}