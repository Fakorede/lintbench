package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.DefaultPosition;
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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo$Builder";
    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";

    private static final String SOURCE_KEY = "services";
    private static final String LOCATION_KEY = "locations";

    private static final String EXPLANATION =
            "The service referenced by a JobInfo.Builder must extend android.app.job.JobService, "
                    + "must be declared in the manifest as a <service>, and that manifest entry "
                    + "must require the android.permission.BIND_JOB_SERVICE permission.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    EXPLANATION,
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
        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        UExpression componentNameArg = args.get(1);
        String serviceClass = resolveServiceClassName(context, componentNameArg);
        if (serviceClass == null) {
            return;
        }

        Map<String, Object> projectMap =
                context.getPartialResults().getProject(context.getProject());
        @SuppressWarnings("unchecked")
        Set<String> services = (Set<String>) projectMap.get(SOURCE_KEY);
        if (services == null) {
            services = new HashSet<>();
            projectMap.put(SOURCE_KEY, services);
        }
        services.add(serviceClass);

        @SuppressWarnings("unchecked")
        Map<String, String> locations = (Map<String, String>) projectMap.get(LOCATION_KEY);
        if (locations == null) {
            locations = new HashMap<>();
            projectMap.put(LOCATION_KEY, locations);
        }
        int line = context.getLocation(node).getStart().getLine();
        locations.put(serviceClass, context.file.getAbsolutePath() + "|" + line);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        Map<String, Object> projectMap = partialResults.getProject(context.getProject());
        if (projectMap == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        Set<String> serviceClasses = (Set<String>) projectMap.get(SOURCE_KEY);
        @SuppressWarnings("unchecked")
        Map<String, String> locations = (Map<String, String>) projectMap.get(LOCATION_KEY);
        if (serviceClasses == null || serviceClasses.isEmpty()) {
            return;
        }

        Map<String, String> manifestServices = parseManifestServices(context);
        JavaEvaluator evaluator = context.getEvaluator();

        for (String className : serviceClasses) {
            PsiClass serviceClass =
                    JavaPsiFacade.getInstance(context.getProject().getPsiProject())
                            .findClass(className, context.getProject().getScope());
            if (serviceClass != null
                    && !evaluator.extendsClass(serviceClass, JOB_SERVICE, true)) {
                report(
                        context,
                        locations,
                        className,
                        "The service " + className + " must extend android.app.job.JobService");
            }

            String permission = manifestServices.get(className);
            if (permission == null) {
                report(
                        context,
                        locations,
                        className,
                        "The service " + className + " must be registered in the manifest");
            } else if (!BIND_JOB_SERVICE.equals(permission)) {
                report(
                        context,
                        locations,
                        className,
                        "The service "
                                + className
                                + " must require the android.permission.BIND_JOB_SERVICE permission");
            }
        }
    }

    private String resolveServiceClassName(JavaContext context, UExpression expression) {
        if (expression == null) {
            return null;
        }

        if (expression instanceof UClassLiteralExpression) {
            PsiType type = ((UClassLiteralExpression) expression).getType();
            PsiClass cls = PsiTypesUtil.getPsiClass(type);
            if (cls != null) {
                return cls.getQualifiedName();
            }
            return null;
        }

        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod method = call.resolve();
            if (method != null
                    && method.isConstructor()
                    && method.getContainingClass() != null
                    && COMPONENT_NAME.equals(method.getContainingClass().getQualifiedName())) {
                List<UExpression> cnArgs = call.getValueArguments();
                if (cnArgs.size() == 2) {
                    UExpression first = cnArgs.get(0);
                    UExpression second = cnArgs.get(1);

                    if (second instanceof UClassLiteralExpression) {
                        PsiType type = ((UClassLiteralExpression) second).getType();
                        PsiClass cls = PsiTypesUtil.getPsiClass(type);
                        if (cls != null) {
                            return cls.getQualifiedName();
                        }
                    }

                    Object pkg = ConstantEvaluator.evaluate(context, first);
                    Object cls = ConstantEvaluator.evaluate(context, second);
                    if (pkg instanceof String && cls instanceof String) {
                        return combineClassName((String) pkg, (String) cls);
                    }
                }
            }
        }

        Object value = ConstantEvaluator.evaluate(context, expression);
        if (value != null && "android.content.ComponentName".equals(value.getClass().getName())) {
            try {
                return (String) value.getClass().getMethod("getClassName").invoke(value);
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private String combineClassName(String packageName, String className) {
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (className.indexOf('.') >= 0) {
            return className;
        }
        return packageName + "." + className;
    }

    private Map<String, String> parseManifestServices(Context context) {
        Map<String, String> services = new HashMap<>();
        org.w3c.dom.Document doc = context.getProject().getManifestDom();
        if (doc == null) {
            return services;
        }

        String packageName = context.getProject().getManifestPackage();
        org.w3c.dom.NodeList list = doc.getElementsByTagName("service");
        for (int i = 0; i < list.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) list.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, "permission");
            if (name != null && !name.isEmpty()) {
                String fqn = getFullyQualifiedClassName(packageName, name);
                services.put(fqn, permission);
            }
        }
        return services;
    }

    private String getFullyQualifiedClassName(String packageName, String className) {
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (className.indexOf('.') < 0 && packageName != null && !packageName.isEmpty()) {
            return packageName + "." + className;
        }
        return className;
    }

    private void report(
            Context context,
            Map<String, String> locations,
            String className,
            String message) {
        Location location;
        String loc = locations != null ? locations.get(className) : null;
        if (loc != null) {
            int separator = loc.lastIndexOf('|');
            String path = loc.substring(0, separator);
            int line = Integer.parseInt(loc.substring(separator + 1));
            File file = new File(path);
            location =
                    Location.create(
                            file,
                            new DefaultPosition(line, 0, -1),
                            new DefaultPosition(line, 0, -1));
        } else {
            location = Location.create(context.getProject().getDir());
        }
        context.report(ISSUE, location, message);
    }
}