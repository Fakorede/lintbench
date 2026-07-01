package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "Registered",
        "Class is not registered in the manifest",
        "Activities, services and content providers should be registered in the " +
        "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
        "`<provider>` tags.\n\n" +
        "If your activity is simply a parent class intended to be " +
        "subclassed by other \"real\" activities, make it an abstract class.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            RegistrationDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    private final Set<String> mRegistered = new HashSet<>();
    private boolean mHasManifests = false;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mRegistered.clear();
        mHasManifests = false;
        List<File> manifestFiles = context.getProject().getManifestFiles();
        for (File manifestFile : manifestFiles) {
            if (manifestFile.exists()) {
                mHasManifests = true;
                parseManifest(context, manifestFile);
            }
        }
    }

    private void parseManifest(@NonNull Context context, File file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            Element root = doc.getDocumentElement();
            if (root != null) {
                String packageName = root.getAttribute("package");
                if (packageName == null || packageName.isEmpty()) {
                    packageName = context.getProject().getPackage();
                }
                if (packageName == null) {
                    packageName = "";
                }
                String[] tags = {"activity", "service", "provider", "activity-alias"};
                for (String tag : tags) {
                    NodeList list = root.getElementsByTagName(tag);
                    for (int i = 0; i < list.getLength(); i++) {
                        Element element = (Element) list.item(i);
                        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                        if (name != null && !name.isEmpty()) {
                            mRegistered.add(resolveClassName(packageName, name).replace('$', '.'));
                        }
                        if ("activity-alias".equals(tag)) {
                            String target = element.getAttributeNS(SdkConstants.ANDROID_URI, "targetActivity");
                            if (target != null && !target.isEmpty()) {
                                mRegistered.add(resolveClassName(packageName, target).replace('$', '.'));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
    }

    private String resolveClassName(String packageName, String className) {
        if (packageName.isEmpty()) {
            if (className.startsWith(".")) {
                return className.substring(1);
            }
            return className;
        }
        if (className.startsWith(".")) {
            return packageName + className;
        } else if (!className.contains(".")) {
            return packageName + "." + className;
        } else {
            return className;
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (node.isInterface() || node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                boolean isComponent = false;
                String componentType = null;

                if (context.getEvaluator().inheritsFrom(node, "android.app.Activity", false)) {
                    isComponent = true;
                    componentType = "Activity";
                } else if (context.getEvaluator().inheritsFrom(node, "android.app.Service", false)) {
                    isComponent = true;
                    componentType = "Service";
                } else if (context.getEvaluator().inheritsFrom(node, "android.content.ContentProvider", false)) {
                    isComponent = true;
                    componentType = "Content Provider";
                }

                if (isComponent && mHasManifests) {
                    String fqcn = node.getQualifiedName();
                    if (fqcn != null && !mRegistered.contains(fqcn)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            String.format("The %1$s `%2$s` is not registered in the manifest", componentType, fqcn)
                        );
                    }
                }
            }
        };
    }
}