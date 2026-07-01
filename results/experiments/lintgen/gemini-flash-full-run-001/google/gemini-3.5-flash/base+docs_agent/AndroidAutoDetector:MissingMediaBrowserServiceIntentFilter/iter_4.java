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
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

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
            EnumSet.of(Scope.JAVA_FILE)
        )
    );

    private List<ManifestServiceInfo> manifestServices = null;

    private static class ManifestServiceInfo {
        String name;
        String manifestPackage;
        String projectPackage;
        boolean hasMediaBrowserIntentFilter;
        boolean isExported;
        Element element;
        File manifestFile;
    }

    @Override
    public void beforeCheckProject(com.android.tools.lint.detector.api.Context context) {
        manifestServices = null;
    }

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
                if (!extendsMediaBrowserService(node, context)) {
                    return;
                }

                String fqName = node.getQualifiedName();
                if (fqName == null) {
                    return;
                }

                ManifestServiceInfo info = findServiceInManifests(context, fqName);

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
                        getLocation(context, info, node),
                        "Service extending `MediaBrowserService` is missing the `android.media.browse.MediaBrowserService` intent-filter."
                    );
                } else if (!info.isExported) {
                    context.report(
                        ISSUE,
                        getLocation(context, info, node),
                        "Service extending `MediaBrowserService` must be exported."
                    );
                }
            }
        };
    }

    private boolean extendsMediaBrowserService(UClass uClass, JavaContext context) {
        return context.getEvaluator().inheritsFrom(uClass, "android.service.media.MediaBrowserService", false)
            || context.getEvaluator().inheritsFrom(uClass, "androidx.media.MediaBrowserServiceCompat", false)
            || context.getEvaluator().inheritsFrom(uClass, "android.support.v4.media.MediaBrowserServiceCompat", false);
    }

    private static String getAndroidAttribute(Element element, String localName) {
        String attr = element.getAttributeNS("http://schemas.android.com/apk/res/android", localName);
        if (attr == null || attr.isEmpty()) {
            attr = element.getAttribute("android:" + localName);
        }
        return attr;
    }

    private ManifestServiceInfo findServiceInManifests(JavaContext context, String classFqName) {
        if (manifestServices == null) {
            manifestServices = new ArrayList<>();
            List<File> manifestFiles = context.getProject().getManifestFiles();
            if (manifestFiles.isEmpty()) {
                manifestFiles = context.getMainProject().getManifestFiles();
            }
            for (File manifestFile : manifestFiles) {
                CharSequence contents;
                try {
                    contents = context.getClient().readFile(manifestFile);
                } catch (Throwable t) {
                    continue;
                }
                Document document = context.getClient().getXmlDocument(manifestFile, contents);
                if (document == null) {
                    continue;
                }
                Element root = document.getDocumentElement();
                if (root == null) {
                    continue;
                }
                String manifestPackage = root.getAttribute("package");
                String projectPackage = context.getProject().getPackage();
                
                NodeList services = root.getElementsByTagName("service");
                for (int i = 0; i < services.getLength(); i++) {
                    Element serviceElement = (Element) services.item(i);
                    String name = getAndroidAttribute(serviceElement, "name");
                    if (name == null || name.isEmpty()) {
                        continue;
                    }
                    
                    ManifestServiceInfo info = new ManifestServiceInfo();
                    info.name = name;
                    info.manifestPackage = manifestPackage;
                    info.projectPackage = projectPackage;
                    info.element = serviceElement;
                    info.manifestFile = manifestFile;
                    
                    boolean hasMediaBrowserIntentFilter = false;
                    boolean hasAnyIntentFilter = false;
                    NodeList children = serviceElement.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        Node child = children.item(j);
                        if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                            hasAnyIntentFilter = true;
                            Element intentFilter = (Element) child;
                            NodeList filterChildren = intentFilter.getChildNodes();
                            for (int k = 0; k < filterChildren.getLength(); k++) {
                                Node filterChild = filterChildren.item(k);
                                if (filterChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                                    Element action = (Element) filterChild;
                                    String actionName = getAndroidAttribute(action, "name");
                                    if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                                        hasMediaBrowserIntentFilter = true;
                                    }
                                }
                            }
                        }
                    }
                    info.hasMediaBrowserIntentFilter = hasMediaBrowserIntentFilter;
                    
                    String exportedAttr = getAndroidAttribute(serviceElement, "exported");
                    if ("true".equals(exportedAttr)) {
                        info.isExported = true;
                    } else if ("false".equals(exportedAttr)) {
                        info.isExported = false;
                    } else {
                        info.isExported = hasAnyIntentFilter;
                    }
                    
                    manifestServices.add(info);
                }
            }
        }
        
        for (ManifestServiceInfo info : manifestServices) {
            if (matches(info.name, classFqName, info.manifestPackage, info.projectPackage)) {
                return info;
            }
        }
        return null;
    }

    private boolean matches(String manifestName, String classFqName, String manifestPackage, String projectPackage) {
        if (manifestName == null || classFqName == null) {
            return false;
        }
        String normalizedManifest = manifestName.replace('$', '.');
        String normalizedClass = classFqName.replace('$', '.');

        if (normalizedManifest.equals(normalizedClass)) {
            return true;
        }

        if (manifestPackage != null && !manifestPackage.isEmpty()) {
            if (normalizedManifest.startsWith(".")) {
                if ((manifestPackage + normalizedManifest).equals(normalizedClass)) {
                    return true;
                }
            } else if (!normalizedManifest.contains(".")) {
                if ((manifestPackage + "." + normalizedManifest).equals(normalizedClass)) {
                    return true;
                }
            }
        }

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

        if (normalizedManifest.startsWith(".")) {
            return normalizedClass.endsWith(normalizedManifest);
        } else if (!normalizedManifest.contains(".")) {
            return normalizedClass.endsWith("." + normalizedManifest);
        }

        return false;
    }

    private Location getLocation(JavaContext context, ManifestServiceInfo info, UClass node) {
        if (info != null && info.manifestFile != null && info.element != null) {
            try {
                Location location = context.getClient().getXmlParser().getLocation(info.manifestFile, info.element);
                if (location != null) {
                    return location;
                }
            } catch (Throwable t) {
                // fallback
            }
        }
        return context.getNameLocation(node);
    }
}