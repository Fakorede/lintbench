package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.PositionXmlParser;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class JobSchedulerDetector extends Detector implements Detector.SourceCodeScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String SERVICE_KEY = "service";

    private static final java.util.List<String> APPLICABLE_TYPES =
            java.util.Arrays.asList("android.app.job.JobInfo$Builder");

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "This check ensures that services used with the JobScheduler API are set up "
                            + "correctly. The service class must extend android.app.job.JobService, "
                            + "it must be registered in the manifest, and the manifest entry must "
                            + "require the android.permission.BIND_JOB_SERVICE permission.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public java.util.List<String> getApplicableConstructorTypes() {
        return APPLICABLE_TYPES;
    }

    @Override
    public void visitConstructor(
            JavaContext context,
            UCallExpression node,
            PsiMethod constructor) {
        java.util.List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression serviceArg = args.get(1);
        PsiClass serviceClass = getServiceClass(serviceArg);
        String serviceName = getServiceName(serviceArg);

        if (serviceClass != null) {
            serviceName = serviceClass.getQualifiedName();
            if (!extendsJobService(serviceClass)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "The service used with JobScheduler must extend " + JOB_SERVICE);
            }
        }

        if (serviceName != null && !serviceName.isEmpty()) {
            context.getPartialResult(ISSUE)
                    .add(
                            SERVICE_KEY,
                            new ServiceInfo(serviceName, context.getLocation(node)));
        }
    }

    @Override
    public void checkPartialResults(Context context, PartialResult partialResults) {
        java.util.Map<String, java.util.List<Object>> map = partialResults.getMap();
        java.util.List<Object> services = map.get(SERVICE_KEY);
        if (services == null || services.isEmpty()) {
            return;
        }

        java.util.Map<String, Boolean> manifestServices = new java.util.HashMap<>();
        for (java.io.File file : context.getMainProject().getManifestFiles()) {
            org.w3c.dom.Document document = PositionXmlParser.parseFileToDocument(file);
            if (document == null) {
                continue;
            }
            org.w3c.dom.Element root = document.getDocumentElement();
            String pkg = root == null ? null : root.getAttribute("package");
            org.w3c.dom.NodeList list = document.getElementsByTagName("service");
            for (int i = 0; i < list.getLength(); i++) {
                org.w3c.dom.Element element = (org.w3c.dom.Element) list.item(i);
                String name = element.getAttributeNS(ANDROID_URI, "name");
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String resolved = resolveServiceName(name, pkg);
                String permission = element.getAttributeNS(ANDROID_URI, "permission");
                Boolean hasPermission = manifestServices.get(resolved);
                if (hasPermission == null || !hasPermission) {
                    manifestServices.put(resolved, BIND_JOB_SERVICE.equals(permission));
                }
            }
        }

        for (Object obj : services) {
            if (!(obj instanceof ServiceInfo)) {
                continue;
            }
            ServiceInfo info = (ServiceInfo) obj;
            Boolean hasPermission = findMatchingService(info.className, manifestServices);
            if (hasPermission == null) {
                context.report(
                        ISSUE,
                        info.location,
                        "The service "
                                + info.className
                                + " used by JobScheduler is not registered in the manifest.");
            } else if (!hasPermission) {
                context.report(
                        ISSUE,
                        info.location,
                        "The service "
                                + info.className
                                + " is registered in the manifest but does not require the "
                                + BIND_JOB_SERVICE
                                + " permission.");
            }
        }
    }

    private static PsiClass getServiceClass(UExpression expression) {
        if (expression instanceof UClassLiteralExpression) {
            PsiType type = ((UClassLiteralExpression) expression).getType();
            if (type instanceof PsiClassType) {
                return ((PsiClassType) type).resolve();
            }
            return null;
        }

        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            String methodName = call.getMethodName();
            if ("ComponentName".equals(methodName)) {
                java.util.List<UExpression> cargs = call.getValueArguments();
                if (cargs.size() >= 2) {
                    return getServiceClass(cargs.get(1));
                }
            }
        }

        return null;
    }

    private static String getServiceName(UExpression expression) {
        if (expression instanceof UClassLiteralExpression) {
            PsiClass cls = getServiceClass(expression);
            return cls == null ? null : cls.getQualifiedName();
        }

        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            String methodName = call.getMethodName();
            if ("ComponentName".equals(methodName)) {
                java.util.List<UExpression> cargs = call.getValueArguments();
                if (cargs.size() >= 2) {
                    UExpression arg = cargs.get(1);
                    if (arg instanceof ULiteralExpression) {
                        Object value = ((ULiteralExpression) arg).getValue();
                        if (value instanceof String) {
                            return (String) value;
                        }
                    } else if (arg instanceof UClassLiteralExpression) {
                        PsiClass cls = getServiceClass(arg);
                        return cls == null ? null : cls.getQualifiedName();
                    }
                }
            }
        }

        return null;
    }

    private static boolean extendsJobService(PsiClass cls) {
        PsiClass current = cls;
        while (current != null) {
            if (JOB_SERVICE.equals(current.getQualifiedName())) {
                return true;
            }
            current = current.getSuperClass();
        }
        return false;
    }

    private static String resolveServiceName(String name, String pkg) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.startsWith(".")) {
            return pkg == null ? name.substring(1) : pkg + name;
        }
        if (name.indexOf('.') != -1) {
            return name;
        }
        return pkg == null || pkg.isEmpty() ? name : pkg + "." + name;
    }

    private static Boolean findMatchingService(
            String className, java.util.Map<String, Boolean> manifestServices) {
        if (className == null) {
            return null;
        }
        Boolean exact = manifestServices.get(className);
        if (exact != null) {
            return exact;
        }

        String simple = className;
        int dot = className.lastIndexOf('.');
        if (dot != -1) {
            simple = className.substring(dot + 1);
        }

        Boolean found = null;
        for (java.util.Map.Entry<String, Boolean> entry : manifestServices.entrySet()) {
            String resolved = entry.getKey();
            if (resolved.equals(className) || resolved.endsWith("." + simple)) {
                if (found == null) {
                    found = entry.getValue();
                } else {
                    found = found && entry.getValue();
                }
            }
        }
        return found;
    }

    private static final class ServiceInfo {
        final String className;
        final Location location;

        ServiceInfo(String className, Location location) {
            this.className = className;
            this.location = location;
        }
    }
}