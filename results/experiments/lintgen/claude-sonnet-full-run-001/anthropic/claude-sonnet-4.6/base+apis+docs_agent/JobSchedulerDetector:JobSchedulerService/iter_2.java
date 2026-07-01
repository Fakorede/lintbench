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

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
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
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";

    // Pending checks: service class name -> (call, context)
    private final Map<String, UCallExpression> mScheduledServices = new HashMap<>();
    private final Map<String, JavaContext> mPendingContexts = new HashMap<>();

    // From manifest: service class name -> has BIND_JOB_SERVICE permission
    private final Map<String, Boolean> mManifestServices = new HashMap<>();

    // Track JobInfo.Builder instances: variable/expression -> service class name
    // We'll track setComponent calls on JobInfo.Builder
    private final Map<String, String> mBuilderToService = new HashMap<>();

    @Override
    public List<String> getApplicableMethodNames() {
        List<String> methods = new ArrayList<>();
        methods.add("schedule");
        methods.add("setComponent");
        return methods;
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        String methodName = call.getMethodName();

        if ("setComponent".equals(methodName)) {
            // Check if this is JobInfo.Builder.setComponent
            if (!evaluator.isMemberInClass(method, JOB_INFO_BUILDER_CLASS)) {
                return;
            }
            List<UExpression> args = call.getValueArguments();
            if (args.size() < 2) {
                return;
            }
            // Second argument should be a ComponentName
            UExpression componentArg = args.get(1);
            String serviceClassName = extractClassNameFromComponentName(context, componentArg);
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

            // Store for later manifest check - use a unique key based on the call
            // We store the service class name associated with this builder call
            String callKey = getCallKey(call);
            if (callKey != null) {
                mBuilderToService.put(callKey, serviceClassName);
            }
            // Also store directly
            mScheduledServices.put(serviceClassName, call);
            mPendingContexts.put(serviceClassName, context);

        } else if ("schedule".equals(methodName)) {
            if (!evaluator.isMemberInClass(method, JOB_SCHEDULER_CLASS)) {
                return;
            }
            // The schedule call takes a JobInfo - we need to find what service it uses
            // This is handled via the setComponent tracking above
        }
    }

    @Nullable
    private String getCallKey(@NonNull UCallExpression call) {
        // Use the source PSI element as a key
        if (call.getSourcePsi() != null) {
            return call.getSourcePsi().toString();
        }
        return null;
    }

    @Nullable
    private String extractClassNameFromComponentName(@NonNull JavaContext context,
            @NonNull UExpression expression) {
        // ComponentName constructor: new ComponentName(context, MyService.class)
        // or new ComponentName(context, "com.example.MyService")
        if (expression instanceof UCallExpression) {
            UCallExpression callExpr = (UCallExpression) expression;
            List<UExpression> args = callExpr.getValueArguments();
            if (args.size() >= 2) {
                UExpression secondArg = args.get(1);
                if (secondArg instanceof UClassLiteralExpression) {
                    UClassLiteralExpression classLiteral = (UClassLiteralExpression) secondArg;
                    com.intellij.psi.PsiType type = classLiteral.getType();
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

        // Also check intent-filter for BIND_JOB_SERVICE action as alternative
        if (!hasBindPermission) {
            // Check children for intent-filter
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element) {
                    Element childEl = (Element) child;
                    if ("intent-filter".equals(childEl.getTagName())) {
                        NodeList actions = childEl.getChildNodes();
                        for (int j = 0; j < actions.getLength(); j++) {
                            Node actionNode = actions.item(j);
                            if (actionNode instanceof Element) {
                                Element actionEl = (Element) actionNode;
                                if ("action".equals(actionEl.getTagName())) {
                                    String actionName = actionEl.getAttributeNS(ANDROID_NS, "name");
                                    if ("android.permission.BIND_JOB_SERVICE".equals(actionName)) {
                                        hasBindPermission = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

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