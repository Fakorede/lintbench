package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String NAMESPACE = "JobSchedulerDetector";
    private static final String MAP_KEY = "services";

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "When using the JobScheduler API the target service must extend "
                            + "android.app.job.JobService, must be registered in the "
                            + "AndroidManifest.xml, and the <service> element must require "
                            + "the android.permission.BIND_JOB_SERVICE permission.",
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
            JavaContext context,
            UCallExpression node,
            PsiMethod constructor) {
        PsiClass containingClass = constructor.getContainingClass();
        if (containingClass == null
                || !JOB_INFO_BUILDER.equals(containingClass.getQualifiedName())) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        String serviceClass = getServiceClass(args.get(1));
        if (serviceClass == null) {
            return;
        }

        PsiClass servicePsi = resolveServiceClass(context, args.get(1), serviceClass);
        if (servicePsi != null && !extendsJobService(servicePsi)) {
            context.report(
                    ISSUE,
                    context.getLocation(node),
                    "The service " + serviceClass + " must extend " + JOB_SERVICE);
            return;
        }

        context.getPartialResult(NAMESPACE)
                .map(MAP_KEY)
                .put(serviceClass, context.getLocation(node));
    }

    @Override
    public void checkPartialResults(
            Context context,
            PartialResult partialResults) {
        Map<String, Object> candidates = partialResults.map(MAP_KEY);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        Map<String, Location> locations = new HashMap<>();
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            if (entry.getValue() instanceof Location) {
                locations.put(entry.getKey(), (Location) entry.getValue());
            }
        }
        if (locations.isEmpty()) {
            return;
        }

        Set<String> declared = new HashSet<>();
        Set<String> permissionOk = new HashSet<>();

        for (java.io.File manifest : context.getProject().getManifestFiles()) {
            CharSequence xml = context.getClient().readFile(manifest);
            if (xml == null) {
                continue;
            }
            org.w3c.dom.Document document =
                    context.getClient().getXmlParser().parseXml(xml, manifest);
            if (document == null) {
                continue;
            }
            String packageName = document.getDocumentElement().getAttribute("package");
            org.w3c.dom.NodeList services = document.getElementsByTagName("service");
            for (int i = 0; i < services.getLength(); i++) {
                org.w3c.dom.Element service = (org.w3c.dom.Element) services.item(i);
                String name = service.getAttributeNS(ANDROID_URI, "name");
                if (name == null || name.isEmpty()) {
                    name = service.getAttribute("name");
                }
                String fqn = resolveServiceName(name, packageName);
                if (fqn == null || !locations.containsKey(fqn)) {
                    continue;
                }
                declared.add(fqn);
                String permission = service.getAttributeNS(ANDROID_URI, "permission");
                if (permission == null || permission.isEmpty()) {
                    permission = service.getAttribute("permission");
                }
                if (BIND_JOB_SERVICE.equals(permission)) {
                    permissionOk.add(fqn);
                }
            }
        }

        for (Map.Entry<String, Location> entry : locations.entrySet()) {
            String fqn = entry.getKey();
            Location location = entry.getValue();
            if (!declared.contains(fqn)) {
                context.report(
                        ISSUE,
                        location,
                        "The service " + fqn + " must be registered in AndroidManifest.xml with the "
                                + BIND_JOB_SERVICE + " permission");
            } else if (!permissionOk.contains(fqn)) {
                context.report(
                        ISSUE,
                        location,
                        "The manifest registration for " + fqn + " must require the "
                                + BIND_JOB_SERVICE + " permission");
            }
        }
    }

    private String getServiceClass(UExpression expression) {
        if (expression instanceof UClassLiteralExpression) {
            PsiType type = ((UClassLiteralExpression) expression).getType();
            if (type instanceof PsiClassType) {
                PsiClass resolved = ((PsiClassType) type).resolve();
                if (resolved != null) {
                    return resolved.getQualifiedName();
                }
            }
        } else if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            if (value instanceof String) {
                return (String) value;
            }
        } else if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod method = call.resolve();
            if (method != null
                    && method.isConstructor()
                    && method.getContainingClass() != null
                    && COMPONENT_NAME.equals(method.getContainingClass().getQualifiedName())) {
                List<UExpression> args = call.getValueArguments();
                if (args.size() == 2) {
                    return getServiceClass(args.get(1));
                }
            }
        }
        return null;
    }

    private PsiClass resolveServiceClass(
            JavaContext context,
            UExpression expression,
            String className) {
        if (expression instanceof UClassLiteralExpression) {
            PsiType type = ((UClassLiteralExpression) expression).getType();
            if (type instanceof PsiClassType) {
                return ((PsiClassType) type).resolve();
            }
        }
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator != null) {
            return evaluator.findClass(className);
        }
        return null;
    }

    private boolean extendsJobService(PsiClass cls) {
        while (cls != null) {
            if (JOB_SERVICE.equals(cls.getQualifiedName())) {
                return true;
            }
            cls = cls.getSuperClass();
        }
        return false;
    }

    private String resolveServiceName(String name, String packageName) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.indexOf('.') == -1) {
            return packageName + "." + name;
        }
        return name;
    }
}