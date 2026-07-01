package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "JobSchedulerService",
                    "JobScheduler problems",
                    "This check looks for various common mistakes in using the JobScheduler API: "
                            + "the service class must extend `JobService`, the service must be registered in the manifest "
                            + "and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        if (node.getValueArguments().size() < 2) {
            return;
        }
        UExpression componentArg = node.getValueArguments().get(1);
        String serviceClassName = getServiceClassName(componentArg);
        if (serviceClassName == null) {
            return;
        }

        // 1. Check if it extends JobService
        PsiClass serviceClass = context.getEvaluator().findClass(serviceClassName);
        if (serviceClass != null) {
            if (!context.getEvaluator().extendsClass(serviceClass, "android.app.job.JobService", false)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Service must extend `android.app.job.JobService`");
            }
        }

        // 2. Store for later manifest check in checkPartialResults
        LintMap map = context.getPartialResults(ISSUE).getMap();
        int count = map.getInteger("service_count") != null ? map.getInteger("service_count") : 0;

        String prefix = "service_" + count + "_";
        map.put(prefix + "name", serviceClassName);

        Location location = context.getLocation(node);
        map.put(prefix + "file", location.getFile().getAbsolutePath());
        if (location.getStart() != null) {
            map.put(prefix + "start", location.getStart().getOffset());
        }
        if (location.getEnd() != null) {
            map.put(prefix + "end", location.getEnd().getOffset());
        }

        map.put("service_count", count + 1);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        org.w3c.dom.Document mergedManifest = context.getClient().getMergedManifest(context.getProject());
        if (mergedManifest == null) {
            return;
        }

        for (java.util.Map.Entry<com.android.tools.lint.detector.api.Project, LintMap> entry : partialResults.getMaps().entrySet()) {
            LintMap map = entry.getValue();
            Integer countVal = map.getInteger("service_count");
            int count = countVal != null ? countVal : 0;
            for (int i = 0; i < count; i++) {
                String prefix = "service_" + i + "_";
                String serviceName = map.getString(prefix + "name");
                String filePath = map.getString(prefix + "file");
                Integer start = map.getInteger(prefix + "start");
                Integer end = map.getInteger(prefix + "end");

                if (serviceName == null || filePath == null) {
                    continue;
                }

                java.io.File file = new java.io.File(filePath);
                Location location;
                if (start != null && end != null) {
                    CharSequence source = context.getClient().getSourceText(file);
                    location = Location.create(file, source, start, end);
                } else {
                    location = Location.create(file);
                }

                org.w3c.dom.Element serviceElement = findServiceElement(mergedManifest, serviceName);
                if (serviceElement == null) {
                    context.report(
                            ISSUE,
                            location,
                            "The service `" + serviceName + "` is not registered in the manifest");
                } else {
                    String permission = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "permission");
                    if (permission.isEmpty()) {
                        permission = serviceElement.getAttribute("android:permission");
                    }
                    if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                        context.report(
                                ISSUE,
                                location,
                                "The service `" + serviceName + "` must require the permission `android.permission.BIND_JOB_SERVICE` in the manifest");
                    }
                }
            }
        }
    }

    private static String getServiceClassName(UExpression componentNameExpr) {
        if (componentNameExpr instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) componentNameExpr;
            boolean isComponentName = false;
            PsiMethod resolved = call.resolve();
            if (resolved != null && resolved.isConstructor() && resolved.getContainingClass() != null) {
                String fqName = resolved.getContainingClass().getQualifiedName();
                if ("android.content.ComponentName".equals(fqName)) {
                    isComponentName = true;
                }
            } else {
                PsiType type = call.getExpressionType();
                if (type != null && "android.content.ComponentName".equals(type.getCanonicalText())) {
                    isComponentName = true;
                }
            }

            if (isComponentName) {
                List<UExpression> args = call.getValueArguments();
                if (args.size() == 2) {
                    UExpression secondArg = args.get(1);
                    if (secondArg instanceof UClassLiteralExpression) {
                        PsiType type = ((UClassLiteralExpression) secondArg).getType();
                        return extractClassName(type);
                    } else {
                        Object value = secondArg.evaluate();
                        if (value instanceof String) {
                            return (String) value;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String extractClassName(PsiType type) {
        if (type == null) return null;
        String canonical = type.getCanonicalText();
        if (canonical.startsWith("java.lang.Class<") && canonical.endsWith(">")) {
            String inner = canonical.substring("java.lang.Class<".length(), canonical.length() - 1);
            if (inner.startsWith("? extends ")) {
                inner = inner.substring("? extends ".length());
            }
            return inner;
        }
        if (canonical.startsWith("kotlin.reflect.KClass<") && canonical.endsWith(">")) {
            String inner = canonical.substring("kotlin.reflect.KClass<".length(), canonical.length() - 1);
            if (inner.startsWith("? extends ")) {
                inner = inner.substring("? extends ".length());
            }
            return inner;
        }
        return canonical;
    }

    private static org.w3c.dom.Element findServiceElement(org.w3c.dom.Document doc, String targetServiceFqName) {
        org.w3c.dom.Element root = doc.getDocumentElement();
        if (root == null) return null;
        String pkg = root.getAttribute("package");

        org.w3c.dom.NodeList services = doc.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Element service = (org.w3c.dom.Element) services.item(i);
            String name = service.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = service.getAttribute("android:name");
            }
            if (name.isEmpty()) {
                continue;
            }

            if (serviceNameMatches(targetServiceFqName, name, pkg)) {
                return service;
            }
        }
        return null;
    }

    private static boolean serviceNameMatches(String target, String manifestName, String pkg) {
        if (pkg == null) {
            pkg = "";
        }
        String fqManifestName = manifestName;
        if (manifestName.startsWith(".")) {
            fqManifestName = pkg + manifestName;
        } else if (!manifestName.contains(".")) {
            fqManifestName = pkg.isEmpty() ? manifestName : pkg + "." + manifestName;
        }

        String normalizedTarget = target.replace('$', '.');
        String normalizedManifest = fqManifestName.replace('$', '.');
        return normalizedTarget.equals(normalizedManifest);
    }
}