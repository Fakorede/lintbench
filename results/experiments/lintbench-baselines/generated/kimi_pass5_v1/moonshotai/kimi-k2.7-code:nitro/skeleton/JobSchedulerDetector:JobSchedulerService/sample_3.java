package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE = "android.app.job.JobService";

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "When using `JobScheduler` to schedule a job, the target service must extend "
                            + "`android.app.job.JobService`, must be declared in the manifest, and "
                            + "the service entry must require the "
                            + "`android.permission.BIND_JOB_SERVICE` permission.",
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
        String className = getServiceClassName(context, node);
        if (className == null) {
            return;
        }

        PsiClass serviceClass = context.getEvaluator().findClass(className);
        if (serviceClass != null
                && !context.getEvaluator().extendsClass(serviceClass, JOB_SERVICE, false)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "The service " + className + " must extend android.app.job.JobService");
            return;
        }

        context.getPartialResults(ISSUE).getMap().put(className, context.getLocation(node));
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        Map<String, Object> map = partialResults.getMap();
        if (map.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        List<File> manifests = context.getMainProject().getManifestFiles();
        Map<String, ServiceEntry> manifestServices = new HashMap<>();
        if (manifests != null) {
            for (File manifest : manifests) {
                if (manifest.exists()) {
                    parseManifest(manifest, packageName, manifestServices);
                }
            }
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String className = entry.getKey();
            Location location = entry.getValue() instanceof Location ? (Location) entry.getValue() : null;
            ServiceEntry service = findMatchingService(className, packageName, manifestServices);
            if (service == null) {
                report(
                        context,
                        location,
                        "The service " + className + " must be registered in the manifest");
            } else if (!BIND_JOB_SERVICE.equals(service.permission)) {
                report(
                        context,
                        location,
                        "The manifest entry for "
                                + className
                                + " must require the android.permission.BIND_JOB_SERVICE permission");
            }
        }
    }

    private String getServiceClassName(JavaContext context, UCallExpression call) {
        List<UExpression> args = call.getValueArguments();
        if (args.size() < 2) {
            return null;
        }
        return getClassNameFromComponent(context, args.get(1));
    }

    private String getClassNameFromComponent(JavaContext context, UExpression expression) {
        String className = getClassNameFromClassLiteral(expression);
        if (className != null) {
            return className;
        }

        if (!(expression instanceof UCallExpression)) {
            return null;
        }

        UCallExpression call = (UCallExpression) expression;
        PsiMethod method = call.resolve();
        if (method == null) {
            return null;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !COMPONENT_NAME.equals(containingClass.getQualifiedName())) {
            return null;
        }

        List<UExpression> args = call.getValueArguments();
        if (args.size() != 2) {
            return null;
        }

        UExpression second = args.get(1);
        className = getClassNameFromClassLiteral(second);
        if (className != null) {
            return className;
        }

        String classString = context.getEvaluator().getConstantString(second);
        if (classString == null) {
            return null;
        }

        if (classString.startsWith(".")) {
            String pkg = context.getEvaluator().getConstantString(args.get(0));
            if (pkg != null) {
                return pkg + classString;
            }
        }

        return classString;
    }

    private String getClassNameFromClassLiteral(UExpression expression) {
        if (!(expression instanceof UClassLiteralExpression)) {
            return null;
        }

        PsiType type = ((UClassLiteralExpression) expression).getType();
        if (!(type instanceof PsiClassType)) {
            return null;
        }

        PsiClassType classType = (PsiClassType) type;
        PsiClass resolved = classType.resolve();
        if (resolved != null && "java.lang.Class".equals(resolved.getQualifiedName())) {
            PsiType[] parameters = classType.getParameters();
            if (parameters.length == 1 && parameters[0] instanceof PsiClassType) {
                PsiClass parameterClass = ((PsiClassType) parameters[0]).resolve();
                return parameterClass != null ? parameterClass.getQualifiedName() : null;
            }
            return null;
        }

        return resolved != null ? resolved.getQualifiedName() : null;
    }

    private void parseManifest(
            File manifest, String packageName, Map<String, ServiceEntry> out) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(manifest);
            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }

            String manifestPackage = root.getAttribute("package");
            if (manifestPackage == null || manifestPackage.isEmpty()) {
                manifestPackage = packageName;
            }
            if (manifestPackage == null) {
                manifestPackage = "";
            }

            NodeList services = document.getElementsByTagName("service");
            for (int i = 0; i < services.getLength(); i++) {
                Node node = services.item(i);
                if (!(node instanceof Element)) {
                    continue;
                }
                Element service = (Element) node;
                String name = service.getAttributeNS(ANDROID_URI, "name");
                if (name == null || name.isEmpty()) {
                    name = service.getAttribute("android:name");
                }
                if (name == null || name.isEmpty()) {
                    continue;
                }

                String normalizedName = normalizeServiceName(manifestPackage, name);
                String permission = service.getAttributeNS(ANDROID_URI, "permission");
                if (permission == null || permission.isEmpty()) {
                    permission = service.getAttribute("android:permission");
                }
                out.put(normalizedName, new ServiceEntry(normalizedName, permission));
            }
        } catch (Exception ignored) {
        }
    }

    private String normalizeServiceName(String packageName, String name) {
        if (name == null) {
            return null;
        }
        if (packageName == null) {
            packageName = "";
        }
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return packageName + "." + name;
    }

    private ServiceEntry findMatchingService(
            String className, String packageName, Map<String, ServiceEntry> services) {
        if (className == null) {
            return null;
        }
        ServiceEntry entry = services.get(className);
        if (entry != null) {
            return entry;
        }

        int index = className.lastIndexOf('.');
        if (index > 0 && index < className.length() - 1) {
            String simpleName = className.substring(index + 1);
            entry = services.get(simpleName);
            if (entry != null) {
                return entry;
            }
            String relativeName = normalizeServiceName(packageName, "." + simpleName);
            entry = services.get(relativeName);
            if (entry != null) {
                return entry;
            }
        }

        return null;
    }

    private void report(Context context, Location location, String message) {
        if (location != null) {
            context.report(ISSUE, location, message);
        }
    }

    private static class ServiceEntry {
        final String name;
        final String permission;

        ServiceEntry(String name, String permission) {
            this.name = name;
            this.permission = permission;
        }
    }
}