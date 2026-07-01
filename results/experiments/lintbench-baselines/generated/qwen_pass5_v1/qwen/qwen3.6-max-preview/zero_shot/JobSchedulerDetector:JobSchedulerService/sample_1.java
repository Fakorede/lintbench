package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobSchedulerDetector extends Detector implements UastScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, the service must be registered in the manifest " +
            "and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
    );

    private Map<String, com.android.tools.lint.detector.api.Location> scheduledServices = new HashMap<>();
    private Map<String, Boolean> manifestServices = new HashMap<>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        scheduledServices.clear();
        manifestServices.clear();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression componentNameArg = args.get(1);
        String serviceClass = extractServiceClass(componentNameArg);
        if (serviceClass == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        PsiClass psiClass = evaluator.findClass(serviceClass);
        if (psiClass != null) {
            if (!evaluator.extendsClass(psiClass, "android.app.job.JobService", false)) {
                context.report(ISSUE, node, context.getLocation(node),
                        "Job service class must extend android.app.job.JobService");
                return;
            }
        }

        scheduledServices.put(serviceClass, context.getLocation(node));
    }

    private static String extractServiceClass(UExpression expression) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            if ("android.content.ComponentName".equals(call.getQualifiedName())) {
                List<UExpression> cnArgs = call.getValueArguments();
                if (cnArgs.size() >= 2) {
                    UExpression classArg = cnArgs.get(1);
                    PsiType type = classArg.getExpressionType();
                    if (type instanceof PsiClassType) {
                        PsiClassType classType = (PsiClassType) type;
                        PsiClassType[] params = classType.getParameters();
                        if (params.length > 0) {
                            return params[0].getCanonicalText();
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Collection<String> getApplicableXmlTags() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if (name.isEmpty()) {
            return;
        }

        String fqcn = context.getFqcn(name);
        if (fqcn == null) {
            fqcn = name;
        }

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, "permission");
        boolean hasBindPermission = "android.permission.BIND_JOB_SERVICE".equals(permission);

        manifestServices.put(fqcn, hasBindPermission);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, com.android.tools.lint.detector.api.Location> entry : scheduledServices.entrySet()) {
            String serviceClass = entry.getKey();
            com.android.tools.lint.detector.api.Location location = entry.getValue();

            if (!manifestServices.containsKey(serviceClass)) {
                context.report(ISSUE, location,
                        "Job service " + serviceClass + " must be registered in the manifest");
            } else if (Boolean.FALSE.equals(manifestServices.get(serviceClass))) {
                context.report(ISSUE, location,
                        "Job service " + serviceClass + " must require android.permission.BIND_JOB_SERVICE permission in the manifest");
            }
        }
    }
}