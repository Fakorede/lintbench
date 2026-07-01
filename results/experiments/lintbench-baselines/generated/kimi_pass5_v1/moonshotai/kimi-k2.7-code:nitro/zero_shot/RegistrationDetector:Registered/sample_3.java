package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TARGET_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY_ALIAS;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.utils.XmlUtils;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_SERVICE = "android.app.Service";
    private static final String ANDROID_PROVIDER = "android.content.ContentProvider";

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the "
                    + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                    + "`<provider>` tags.\n\n"
                    + "If your activity is simply a parent class intended to be subclassed "
                    + "by other \"real\" activities, make it an abstract class.\n\n"
                    + "More information: https://developer.android.com/guide/topics/manifest/manifest-intro.html",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private final Set<String> mRegistered = new HashSet<>();
    private final Set<String> mProjectClasses = new HashSet<>();

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mRegistered.clear();
        mProjectClasses.clear();

        List<? extends Project> projects = context.getDriver().getProjects();
        if (projects != null) {
            for (Project project : projects) {
                File manifest = project.getManifest();
                if (manifest != null && manifest.exists()) {
                    parseManifest(manifest);
                }
            }
        }
    }

    @Override
    public List<Class<? extends org.jetbrains.uast.UElement>> getApplicableUObjectTypes() {
        return java.util.Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUElementHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                checkClass(context, node);
            }
        };
    }

    private void checkClass(@NotNull JavaContext context, @NotNull UClass node) {
        if (!(node.getPsi() instanceof PsiClass)) {
            return;
        }

        PsiClass psiClass = (PsiClass) node.getPsi();
        if (psiClass.isInterface() || psiClass.isAnnotationType() || psiClass.isEnum()) {
            return;
        }

        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String fqName = psiClass.getQualifiedName();
        if (fqName == null) {
            return;
        }

        mProjectClasses.add(fqName);

        String tag = getComponentTag(psiClass);
        if (tag == null) {
            return;
        }

        if (!mRegistered.contains(fqName)) {
            String typeName;
            if (TAG_ACTIVITY.equals(tag)) {
                typeName = "activity";
            } else if (TAG_SERVICE.equals(tag)) {
                typeName = "service";
            } else {
                typeName = "content provider";
            }

            String message = String.format(
                    "The %s %s is not registered in the AndroidManifest.xml file; "
                            + "add it using a <%s> tag.",
                    typeName, fqName, tag);
            context.report(ISSUE, node, context.getLocation(node), message);
        }
    }

    private static String getComponentTag(@NotNull PsiClass psiClass) {
        PsiClass current = psiClass.getSuperClass();
        while (current != null) {
            String name = current.getQualifiedName();
            if (ANDROID_ACTIVITY.equals(name)) {
                return TAG_ACTIVITY;
            }
            if (ANDROID_SERVICE.equals(name)) {
                return TAG_SERVICE;
            }
            if (ANDROID_PROVIDER.equals(name)) {
                return TAG_PROVIDER;
            }
            current = current.getSuperClass();
        }
        return null;
    }

    private void parseManifest(@NotNull File manifestFile) {
        Document document = XmlUtils.parseDocumentSilently(manifestFile, true);
        if (document == null) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null || !"manifest".equals(root.getTagName())) {
            return;
        }

        String packageName = root.getAttribute("package");
        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (TAG_APPLICATION.equals(element.getTagName())) {
                parseApplication(element, packageName);
            }
        }
    }

    private void parseApplication(@NotNull Element application, @NotNull String packageName) {
        NodeList children = application.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String tag = element.getTagName();

            if (TAG_ACTIVITY.equals(tag)
                    || TAG_SERVICE.equals(tag)
                    || TAG_PROVIDER.equals(tag)) {
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                addRegisteredName(name, packageName);
            } else if (TAG_ACTIVITY_ALIAS.equals(tag)) {
                String target = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_ACTIVITY);
                addRegisteredName(target, packageName);
            }
        }
    }

    private void addRegisteredName(String name, String packageName) {
        if (name == null || name.isEmpty()) {
            return;
        }
        String resolved = resolveManifestName(name, packageName);
        if (resolved != null && !resolved.isEmpty()) {
            mRegistered.add(resolved);
        }
    }

    private static String resolveManifestName(@NotNull String name, @NotNull String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.indexOf('.') < 0) {
            return packageName + '.' + name;
        }
        return name;
    }
}