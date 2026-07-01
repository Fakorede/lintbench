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
import com.android.tools.lint.detector.api.PositionXmlParser;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler Service Problems",
                            "When using `JobScheduler`, the service referenced by a "
                                    + "`JobInfo.Builder` must extend `android.app.job.JobService`, "
                                    + "must be registered in `AndroidManifest.xml`, and that "
                                    + "`<service>` declaration must require the "
                                    + "`android.permission.BIND_JOB_SERVICE` permission.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public JobSchedulerDetector() {}

    private static class ServiceReference {
        final String className;
        final Location location;
        final Boolean extendsJobService;

        ServiceReference(String className, Location location, Boolean extendsJobService) {
            this.className = className;
            this.location = location;
            this.extendsJobService = extendsJobService;
        }
    }

    private final Map<Project, List<ServiceReference>> mReferences = new HashMap<>();

    @Override
    public List<String> applicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER);
    }

    @Override
    public void visitConstructor(JavaContext context, UCallExpression call, PsiMethod constructor) {
        List<UExpression> args = call.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        UExpression serviceArg = args.get(1);
        String className = getServiceClassName(serviceArg);
        if (className == null) {
            return;
        }

        PsiClass psiClass = resolveServiceClass(serviceArg);
        if (psiClass == null) {
            JavaEvaluator evaluator = context.getEvaluator();
            psiClass = evaluator.findClass(className);
        }

        Boolean extendsJobService = null;
        if (psiClass != null) {
            extendsJobService = context.getEvaluator().extendsClass(psiClass, JOB_SERVICE, false);
        }

        Location location = context.getLocation(call);
        List<ServiceReference> list = mReferences.get(context.getProject());
        if (list == null) {
            list = new ArrayList<>();
            mReferences.put(context.getProject(), list);
        }
        list.add(new ServiceReference(className, location, extendsJobService));
    }

    @Override
    public void checkPartialResults(Context context, PartialResult partialResult) {
        for (Map.Entry<Project, List<ServiceReference>> entry : mReferences.entrySet()) {
            Project project = entry.getKey();
            List<ServiceReference> references = entry.getValue();
            Map<String, Element> manifestServices = parseManifestServices(project);

            for (ServiceReference ref : references) {
                if (Boolean.FALSE.equals(ref.extendsJobService)) {
                    context.report(
                            ISSUE,
                            ref.location,
                            "The service `"
                                    + ref.className
                                    + "` used with `JobInfo.Builder` must extend "
                                    + "`android.app.job.JobService`.");
                }

                String expectedName = resolveServiceName(project.getPackage(), ref.className);
                if (expectedName == null) {
                    continue;
                }

                Element service = manifestServices.get(expectedName);
                if (service == null) {
                    context.report(
                            ISSUE,
                            ref.location,
                            "The service `"
                                    + ref.className
                                    + "` must be registered in `AndroidManifest.xml`.");
                } else {
                    String permission = service.getAttributeNS(ANDROID_URI, "permission");
                    if (permission == null || permission.isEmpty()) {
                        permission = service.getAttribute("permission");
                    }
                    if (!BIND_JOB_SERVICE.equals(permission)) {
                        context.report(
                                ISSUE,
                                ref.location,
                                "The service `"
                                        + ref.className
                                        + "` must require the `android.permission.BIND_JOB_SERVICE` "
                                        + "permission in `AndroidManifest.xml`.");
                    }
                }
            }
        }
        mReferences.clear();
    }

    private static Map<String, Element> parseManifestServices(Project project) {
        Map<String, Element> services = new HashMap<>();
        List<File> manifests = project.getManifestFiles();
        if (manifests == null) {
            return services;
        }

        String packageName = project.getPackage();
        for (File manifest : manifests) {
            Document document;
            try {
                document = PositionXmlParser.parse(manifest);
            } catch (Exception e) {
                continue;
            }
            if (document == null) {
                continue;
            }

            Element root = document.getDocumentElement();
            if (root == null) {
                continue;
            }

            NodeList nodes = root.getElementsByTagName("service");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element)) {
                    continue;
                }
                Element element = (Element) node;
                String name = element.getAttributeNS(ANDROID_URI, "name");
                if (name == null || name.isEmpty()) {
                    name = element.getAttribute("name");
                }
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String resolved = resolveServiceName(packageName, name);
                if (resolved != null) {
                    services.put(resolved, element);
                }
            }
        }
        return services;
    }

    private static String resolveServiceName(String packageName, String className) {
        if (className == null) {
            return null;
        }
        if (className.startsWith(".")) {
            return packageName != null ? packageName + className : null;
        }
        if (!className.contains(".")) {
            return packageName != null ? packageName + "." + className : null;
        }
        return className;
    }

    private static String getServiceClassName(UExpression expression) {
        PsiClass cls = resolveServiceClass(expression);
        if (cls != null) {
            return cls.getQualifiedName();
        }

        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            if (value instanceof String) {
                return (String) value;
            }
        }

        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod method = resolveConstructor(call);
            if (method != null && COMPONENT_NAME.equals(getQualifiedName(method.getContainingClass()))) {
                List<UExpression> args = call.getValueArguments();
                if (args.size() >= 2) {
                    return getServiceClassName(args.get(1));
                }
            }
        }

        return null;
    }

    private static PsiClass resolveServiceClass(UExpression expression) {
        if (expression instanceof UClassLiteralExpression) {
            PsiType type = ((UClassLiteralExpression) expression).getType();
            if (type instanceof PsiClassType) {
                return ((PsiClassType) type).resolve();
            }
            return null;
        }

        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod method = resolveConstructor(call);
            if (method != null && COMPONENT_NAME.equals(getQualifiedName(method.getContainingClass()))) {
                List<UExpression> args = call.getValueArguments();
                if (args.size() >= 2) {
                    return resolveServiceClass(args.get(1));
                }
            }
        }

        return null;
    }

    private static PsiMethod resolveConstructor(UCallExpression call) {
        if (call.resolve() instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) call.resolve();
            if (method.isConstructor()) {
                return method;
            }
        }
        return null;
    }

    private static String getQualifiedName(PsiClass cls) {
        return cls == null ? null : cls.getQualifiedName();
    }
}