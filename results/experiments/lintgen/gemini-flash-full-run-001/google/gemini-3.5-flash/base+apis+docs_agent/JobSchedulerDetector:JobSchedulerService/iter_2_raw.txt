package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "JobSchedulerService",
        "JobScheduler problems",
        "This check looks for various common mistakes in using the JobScheduler API: " +
        "the service class must extend `JobService`, the service must be registered in " +
        "the manifest and the registration must require the permission " +
        "`android.permission.BIND_JOB_SERVICE`.",
        Category.CORRECTNESS,
        5,
        Severity.ERROR,
        new Implementation(
            JobSchedulerDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                JobSchedulerDetector.this.checkClass(context, node);
            }
        };
    }

    private void checkClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface() || declaration.isEnum()) {
            return;
        }
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String className = declaration.getQualifiedName();
        if (className == null) {
            return;
        }

        Document mergedManifest = context.getProject().getMergedManifest();
        if (mergedManifest == null) {
            return;
        }

        NodeList services = mergedManifest.getElementsByTagName(SdkConstants.TAG_SERVICE);
        Element serviceElement = null;
        String normalizedClassName = className.replace('$', '.');

        for (int i = 0; i < services.getLength(); i++) {
            Element element = (Element) services.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                if (name.startsWith(".")) {
                    String pkg = mergedManifest.getDocumentElement().getAttribute("package");
                    name = pkg + name;
                } else if (!name.contains(".")) {
                    String pkg = mergedManifest.getDocumentElement().getAttribute("package");
                    name = pkg + "." + name;
                }

                String normalizedName = name.replace('$', '.');
                if (normalizedName.equals(normalizedClassName)) {
                    serviceElement = element;
                    break;
                }
            }
        }

        boolean extendsJobService = context.getEvaluator().inheritsFrom(declaration, "android.app.job.JobService", false);

        if (extendsJobService) {
            if (serviceElement == null) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Scheduled job service `" + className + "` is not registered in the manifest"
                );
            } else {
                String permission = serviceElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
                if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    context.report(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        "JobService `" + className + "` must require `android.permission.BIND_JOB_SERVICE` permission in the manifest"
                    );
                }
            }
        } else {
            if (serviceElement != null) {
                String permission = serviceElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
                if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    context.report(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        "This class must extend `android.app.job.JobService`"
                    );
                }
            }
        }
    }
}