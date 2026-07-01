package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_MANIFEST;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.visitor.UElementHandler;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegistrationDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String CLASS_SERVICE = "android.app.Service";
    private static final String CLASS_CONTENT_PROVIDER = "android.content.ContentProvider";

    private static final Implementation IMPLEMENTATION = new Implementation(
            RegistrationDetector.class,
            Scope.JAVA_FILE_SCOPE,
            Scope.MANIFEST_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the "
                    + "AndroidManifest.xml file using `<activity>`, `<service>` and "
                    + "`<provider>` tags.\n\n"
                    + "If your activity is simply a parent class intended to be subclassed "
                    + "by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    private final Map<String, Node> mRegisteredClasses = new HashMap<>();
    private String mPackageName;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_MANIFEST,
                TAG_ACTIVITY,
                TAG_SERVICE,
                TAG_PROVIDER
        );
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Node node) {
        String tag = node.getNodeName();
        if (TAG_MANIFEST.equals(tag)) {
            mPackageName = ((Element) node).getAttribute(ATTR_PACKAGE);
            return;
        }

        String name = ((Element) node).getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = mPackageName != null ? mPackageName : "";
        String fqcn = getFullyQualifiedClassName(packageName, name);
        mRegisteredClasses.put(fqcn, node);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>singletonList(UClass.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new ClassVisitor(context);
    }

    private class ClassVisitor extends UElementHandler {
        private final JavaContext mContext;

        ClassVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitClass(@NotNull UClass node) {
            PsiClass psiClass = node.getJavaPsi();
            if (psiClass == null) {
                return;
            }

            if (node.isInterface() || node.isEnum() || node.isAnnotationType()) {
                return;
            }

            JavaEvaluator evaluator = mContext.getEvaluator();
            if (evaluator.isAbstract(psiClass)) {
                return;
            }

            String fqcn = node.getQualifiedName();
            if (fqcn == null) {
                return;
            }

            boolean isComponent = false;
            if (evaluator.extendsClass(psiClass, CLASS_ACTIVITY, true)) {
                isComponent = true;
            } else if (evaluator.extendsClass(psiClass, CLASS_SERVICE, true)) {
                isComponent = true;
            } else if (evaluator.extendsClass(psiClass, CLASS_CONTENT_PROVIDER, true)) {
                isComponent = true;
            }

            if (isComponent && !mRegisteredClasses.containsKey(fqcn)) {
                String message = fqcn + " is not registered in the manifest";
                mContext.report(ISSUE, node, mContext.getLocation(node), message);
            }
        }
    }

    private static String getFullyQualifiedClassName(String packageName, String className) {
        if (className.startsWith(".")) {
            return packageName + className;
        } else if (className.contains(".")) {
            return className;
        } else {
            return packageName + "." + className;
        }
    }
}