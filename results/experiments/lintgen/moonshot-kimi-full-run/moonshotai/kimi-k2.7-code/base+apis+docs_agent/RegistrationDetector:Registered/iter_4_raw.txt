package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegistrationDetector extends Detector
        implements Detector.ClassScanner, Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            RegistrationDetector.class,
            Scope.JAVA_FILE_SCOPE,
            Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the " +
            "`AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags. " +
            "If your activity is simply a parent class intended to be subclassed by other " +
            "\"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    private final Map<Project, Set<String>> mRegistered = new HashMap<>();
    private final Map<Project, List<Pair<String, Location>>> mClasses = new HashMap<>();

    @Override
    public void beforeCheckEachProject(Context context) {
        Project project = context.getProject();
        mRegistered.put(project, new HashSet<String>());
        mClasses.put(project, new ArrayList<Pair<String, Location>>());
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        Set<String> registered = mRegistered.get(project);
        List<Pair<String, Location>> classes = mClasses.get(project);
        if (registered == null || classes == null) {
            return;
        }

        for (Pair<String, Location> pair : classes) {
            String name = pair.first;
            Location location = pair.second;
            if (!registered.contains(name)) {
                context.report(ISSUE, location,
                        "Class " + name + " is not registered in the manifest");
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                SdkConstants.CLASS_ACTIVITY,
                SdkConstants.CLASS_SERVICE,
                "android.content.ContentProvider");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String name = declaration.getQualifiedName();
        if (name == null) {
            return;
        }

        Project project = context.getProject();
        List<Pair<String, Location>> list = mClasses.get(project);
        if (list != null) {
            list.add(new Pair<String, Location>(name,
                    context.getLocation((UElement) declaration)));
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_ACTIVITY,
                SdkConstants.TAG_SERVICE,
                SdkConstants.TAG_PROVIDER);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = resolveManifestName(context, element);
        if (name == null || name.isEmpty()) {
            return;
        }

        Project project = context.getProject();
        Set<String> registered = mRegistered.get(project);
        if (registered != null) {
            registered.add(name);
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == null || folderType == ResourceFolderType.XML;
    }

    private static String resolveManifestName(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return null;
        }

        Document document = context.document;
        if (document == null) {
            return name;
        }

        Element manifest = document.getDocumentElement();
        if (manifest == null) {
            return name;
        }

        String pkg = manifest.getAttribute(SdkConstants.ATTR_PACKAGE);
        if (pkg == null || pkg.isEmpty()) {
            return name;
        }

        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }

        return name;
    }

    private static class Pair<F, S> {
        final F first;
        final S second;

        Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
}