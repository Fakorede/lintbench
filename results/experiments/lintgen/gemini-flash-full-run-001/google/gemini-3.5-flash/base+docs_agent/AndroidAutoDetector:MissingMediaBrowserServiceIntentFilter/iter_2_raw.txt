package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.TAG_SERVICE;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingMediaBrowserServiceIntentFilter",
        "Missing MediaBrowserService intent-filter",
        "An Automotive Media App requires an exported service that extends " +
        "`android.service.media.MediaBrowserService` with an `intent-filter` " +
        "for the action `android.media.browse.MediaBrowserService` to be able " +
        "to browse and play media.\n\n" +
        "To do this, add\n" +
        "```xml\n" +
        "<intent-filter>\n" +
        "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
        "</intent-filter>\n" +
        "```\n" +
        "to the service that extends `android.service.media.MediaBrowserService`.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(
            AndroidAutoDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
        )
    );

    private final List<ServiceInfo> manifestServices = new ArrayList<>();
    private String manifestPackageName = null;

    private static class ServiceInfo {
        final String name;
        final boolean isExported;
        final boolean hasMediaBrowserIntentFilter;
        final Location location;

        ServiceInfo(String name, boolean isExported, boolean hasMediaBrowserIntentFilter, Location location) {
            this.name = name;
            this.isExported = isExported;
            this.hasMediaBrowserIntentFilter = hasMediaBrowserIntentFilter;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckRootProject(com.android.tools.lint.detector.api.Context context) {
        manifestServices.clear();
        manifestPackageName = null;
    }

    // XmlScanner implementation

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }
        
        if (manifestPackageName == null) {
            manifestPackageName = element.getOwnerDocument().getDocumentElement().getAttribute(ATTR_PACKAGE);
        }

        boolean hasMediaBrowserIntentFilter = false;
        boolean hasAnyIntentFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                hasAnyIntentFilter = true;
                Element intentFilter = (Element) child;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                        Element action = (Element) filterChild;
                        String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                            hasMediaBrowserIntentFilter = true;
                        }
                    }
                }
            }
        }

        String exportedAttr = element.getAttributeNS(ANDROID_URI, "exported");
        boolean isExported;
        if ("true".equals(exportedAttr)) {
            isExported = true;
        } else if ("false".equals(exportedAttr)) {
            isExported = false;
        } else {
            isExported = hasAnyIntentFilter;
        }

        Location location = context.getNameLocation(element);
        manifestServices.add(new ServiceInfo(name, isExported, hasMediaBrowserIntentFilter, location));
    }

    // SourceCodeScanner implementation

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (context.getEvaluator().isAbstract(node)) {
                    return;
                }
                if (!extendsMediaBrowserService(node)) {
                    return;
                }

                String fqName = node.getQualifiedName();
                if (fqName == null) {
                    return;
                }

                // Find matching service in manifestServices
                ServiceInfo info = null;
                String projectPackage = context.getProject().getPackage();
                for (ServiceInfo service : manifestServices) {
                    if (matches(service.name, fqName, projectPackage)) {
                        info = service;
                        break;
                    }
                }

                if (info == null) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Service extending `MediaBrowserService` must be declared in the manifest with an intent-filter for `android.media.browse.MediaBrowserService`."
                    );
                } else if (!info.hasMediaBrowserIntentFilter) {
                    context.report(
                        ISSUE,
                        info.location,
                        "Service extending `MediaBrowserService` is missing the `android.media.browse.MediaBrowserService` intent-filter."
                    );
                } else if (!info.isExported) {
                    context.report(
                        ISSUE,
                        info.location,
                        "Service extending `MediaBrowserService` must be exported."
                    );
                }
            }
        };
    }

    private boolean extendsMediaBrowserService(UClass uClass) {
        // Check direct supertypes
        for (PsiClassType type : uClass.getSuperTypes()) {
            String name = type.getCanonicalText();
            if (isMediaBrowserService(name)) {
                return true;
            }
        }
        // Check super class
        PsiClass superClass = uClass.getSuperClass();
        while (superClass != null) {
            String qName = superClass.getQualifiedName();
            if (isMediaBrowserService(qName)) {
                return true;
            }
            superClass = superClass.getSuperClass();
        }
        return false;
    }

    private boolean isMediaBrowserService(String name) {
        if (name == null) {
            return false;
        }
        return "android.service.media.MediaBrowserService".equals(name)
                || "MediaBrowserService".equals(name)
                || name.endsWith(".MediaBrowserService");
    }

    private boolean matches(String manifestName, String classFqName, String projectPackage) {
        if (manifestName == null || classFqName == null) {
            return false;
        }
        String normalizedManifest = manifestName.replace('$', '.');
        String normalizedClass = classFqName.replace('$', '.');

        if (normalizedManifest.equals(normalizedClass)) {
            return true;
        }

        // Try with manifest package name first
        if (manifestPackageName != null && !manifestPackageName.isEmpty()) {
            if (normalizedManifest.startsWith(".")) {
                if ((manifestPackageName + normalizedManifest).equals(normalizedClass)) {
                    return true;
                }
            } else if (!normalizedManifest.contains(".")) {
                if ((manifestPackageName + "." + normalizedManifest).equals(normalizedClass)) {
                    return true;
                }
            }
        }

        // Try with project package name
        if (projectPackage != null && !projectPackage.isEmpty()) {
            if (normalizedManifest.startsWith(".")) {
                if ((projectPackage + normalizedManifest).equals(normalizedClass)) {
                    return true;
                }
            } else if (!normalizedManifest.contains(".")) {
                if ((projectPackage + "." + normalizedManifest).equals(normalizedClass)) {
                    return true;
                }
            }
        }

        // Fallback: check endsWith
        if (normalizedManifest.startsWith(".")) {
            return normalizedClass.endsWith(normalizedManifest);
        } else if (!normalizedManifest.contains(".")) {
            return normalizedClass.endsWith("." + normalizedManifest);
        }

        return false;
    }
}