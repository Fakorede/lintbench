package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "When using the JobScheduler API, the service class must extend "
                            + "android.app.job.JobService, must be declared in AndroidManifest.xml, "
                            + "and the declaration must require the "
                            + "android.permission.BIND_JOB_SERVICE permission.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(COMPONENT_NAME);
    }

    @Override
    public void visitConstructor(
            JavaContext context,
            UCallExpression node,
            PsiMethod constructor) {
        if (!isUsedForJobScheduler(node, context)) {
            return;
        }

        String serviceClass = getServiceClassName(constructor, node.getValueArguments());
        if (serviceClass == null) {
            return;
        }

        PsiClass servicePsiClass = context.getEvaluator().findClass(serviceClass);
        if (servicePsiClass == null) {
            return;
        }

        if (!context.getEvaluator().extendsClass(servicePsiClass, JOB_SERVICE, false)) {
            context.report(
                    ISSUE,
                    node,
                    "The service class `" + serviceClass + "` must extend " + JOB_SERVICE + ".");
            return;
        }

        PartialResult partialResult = context.getPartialResults(ISSUE);
        if (partialResult == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Location> locations =
                (List<Location>) partialResult.getMap().get(serviceClass);
        if (locations == null) {
            locations = new ArrayList<>();
            partialResult.getMap().put(serviceClass, locations);
        }
        locations.add(context.getLocation(node));
    }

    @Override
    public void checkPartialResults(
            Context context,
            PartialResult partialResults) {
        Map<String, Boolean> manifestServices = readManifestServices(context);
        if (manifestServices == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : partialResults.getMap().entrySet()) {
            String serviceClass = entry.getKey();
            @SuppressWarnings("unchecked")
            List<Location> locations = (List<Location>) entry.getValue();

            Boolean hasPermission = manifestServices.get(serviceClass);
            if (hasPermission == null) {
                for (Location location : locations) {
                    context.report(
                            ISSUE,
                            location,
                            "The service `" + serviceClass + "` must be registered in the manifest.");
                }
            } else if (!hasPermission) {
                for (Location location : locations) {
                    context.report(
                            ISSUE,
                            location,
                            "The service `" + serviceClass + "` must require the permission "
                                    + BIND_JOB_SERVICE + " in the manifest.");
                }
            }
        }
    }

    private static Map<String, Boolean> readManifestServices(Context context) {
        org.w3c.dom.Document manifest = context.getMainProject().getManifestDom();
        if (manifest == null) {
            return null;
        }

        org.w3c.dom.Element root = manifest.getDocumentElement();
        String packageName = root == null ? "" : root.getAttribute("package");

        Map<String, Boolean> services = new HashMap<>();
        org.w3c.dom.NodeList serviceNodes = manifest.getElementsByTagName("service");
        for (int i = 0; i < serviceNodes.getLength(); i++) {
            org.w3c.dom.Node item = serviceNodes.item(i);
            if (!(item instanceof org.w3c.dom.Element)) {
                continue;
            }
            org.w3c.dom.Element service = (org.w3c.dom.Element) item;
            String name = service.getAttribute("android:name");
            if (name.isEmpty()) {
                continue;
            }
            String fqcn = resolveServiceName(name, packageName);
            boolean hasPermission = BIND_JOB_SERVICE.equals(service.getAttribute("android:permission"));
            services.put(fqcn, hasPermission);
        }
        return services;
    }

    private static String resolveServiceName(String name, String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return packageName + "." + name;
    }

    private static boolean isUsedForJobScheduler(UCallExpression node, JavaContext context) {
        UElement parent = node.getUastParent();
        while (parent != null) {
            if (parent instanceof UCallExpression) {
                UCallExpression call = (UCallExpression) parent;
                PsiMethod method = call.resolve();
                if (method != null) {
                    PsiClass containingClass = method.getContainingClass();
                    String className = containingClass != null ? containingClass.getQualifiedName() : null;
                    if (className != null) {
                        className = className.replace('$', '.');
                    }
                    if (JOB_INFO_BUILDER.equals(className)
                            && (method.isConstructor() || "setService".equals(method.getName()))) {
                        return true;
                    }
                }
            }
            parent = parent.getUastParent();
        }
        return false;
    }

    private static String getServiceClassName(
            PsiMethod constructor,
            List<UExpression> arguments) {
        PsiParameter[] parameters = constructor.getParameterList().getParameters();
        if (parameters.length != 2 || arguments.size() != 2) {
            return null;
        }

        String firstType = parameters[0].getType().getCanonicalText();
        String secondType = parameters[1].getType().getCanonicalText();

        if ("java.lang.String".equals(firstType) && "java.lang.String".equals(secondType)) {
            Object pkg = getLiteralValue(arguments.get(0));
            Object cls = getLiteralValue(arguments.get(1));
            if (pkg instanceof String && cls instanceof String) {
                return resolveServiceName((String) cls, (String) pkg);
            }
            return null;
        }

        if (firstType != null && firstType.contains("Context")) {
            UExpression second = arguments.get(1);

            if (secondType != null && secondType.contains("Class")) {
                Object value = getLiteralValue(second);
                if (value instanceof PsiType) {
                    PsiType type = (PsiType) value;
                    if (type instanceof PsiClassType) {
                        PsiClass resolved = ((PsiClassType) type).resolve();
                        if (resolved != null) {
                            return resolved.getQualifiedName();
                        }
                    }
                }
            } else if ("java.lang.String".equals(secondType)) {
                Object value = getLiteralValue(second);
                if (value instanceof String) {
                    String name = (String) value;
                    if (name.contains(".") && !name.startsWith(".")) {
                        return name;
                    }
                }
            }
        }

        return null;
    }

    private static Object getLiteralValue(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            return ((ULiteralExpression) expression).getValue();
        }
        return null;
    }
}