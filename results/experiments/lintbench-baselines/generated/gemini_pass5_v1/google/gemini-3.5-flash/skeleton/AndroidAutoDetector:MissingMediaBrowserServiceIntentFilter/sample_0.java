package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiModifier;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends " +
                    "android.service.media.MediaBrowserService with an intent-filter " +
                    "for the action android.media.browse.MediaBrowserService to be able " +
                    "to browse and play media.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final java.util.Set<String> mServicesWithFilter = new java.util.HashSet<>();
    private final java.util.Set<String> mDeclaredServices = new java.util.HashSet<>();
    private boolean mManifestScanned = false;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mServicesWithFilter.clear();
        mDeclaredServices.clear();
        mManifestScanned = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("service".equals(element.getTagName())) {
            mManifestScanned = true;
            String name = getAndroidAttribute(element, "name");
            if (name != null && !name.isEmpty()) {
                String fqcn = resolveClassName(context, name);
                mDeclaredServices.add(fqcn);
                if (hasMediaBrowserServiceIntentFilter(element)) {
                    mServicesWithFilter.add(fqcn);
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.getModifierList() != null && declaration.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT)) {
            return;
        }
        if (declaration.isInterface()) {
            return;
        }

        String fqcn = declaration.getQualifiedName();
        if (fqcn == null) {
            return;
        }

        checkManifestOnDemand(context);

        if (!mServicesWithFilter.contains(fqcn)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Service " + declaration.getName() + " missing MediaBrowserService intent-filter");
        }
    }

    private void checkManifestOnDemand(Context context) {
        if (mManifestScanned) {
            return;
        }
        mManifestScanned = true;
        List<java.io.File> manifestFiles = context.getProject().getManifestFiles();
        for (java.io.File file : manifestFiles) {
            if (file.exists()) {
                try {
                    javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                    org.w3c.dom.Document doc = builder.parse(file);
                    org.w3c.dom.NodeList services = doc.getElementsByTagName("service");
                    for (int i = 0; i < services.getLength(); i++) {
                        org.w3c.dom.Node node = services.item(i);
                        if (node instanceof Element) {
                            Element element = (Element) node;
                            String name = getAndroidAttribute(element, "name");
                            if (name != null && !name.isEmpty()) {
                                String fqcn = resolveClassName(context, name);
                                mDeclaredServices.add(fqcn);
                                if (hasMediaBrowserServiceIntentFilter(element)) {
                                    mServicesWithFilter.add(fqcn);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private boolean hasMediaBrowserServiceIntentFilter(Element serviceElement) {
        org.w3c.dom.NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                org.w3c.dom.NodeList actions = intentFilter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    org.w3c.dom.Node actionNode = actions.item(j);
                    if (actionNode instanceof Element) {
                        Element action = (Element) actionNode;
                        String name = getAndroidAttribute(action, "name");
                        if ("android.media.browse.MediaBrowserService".equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private String getAndroidAttribute(Element element, String localName) {
        String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        return value;
    }

    private String resolveClassName(Context context, String className) {
        if (className.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + className;
            }
        } else if (!className.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + "." + className;
            }
        }
        return className;
    }
}