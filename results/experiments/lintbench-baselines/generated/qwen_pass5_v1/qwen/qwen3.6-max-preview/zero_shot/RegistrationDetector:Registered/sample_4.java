package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.*;

import java.io.File;
import java.util.*;

public class RegistrationDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "Registered",
        "Class is not registered in the manifest",
        "Activities, services and content providers should be registered in the " +
        "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
        "`<provider>` tags.\n\n" +
        "If your activity is simply a parent class intended to be " +
        "subclassed by other \"real\" activities, make it an abstract class.",
        Category.CORRECTNESS, 6, Severity.WARNING,
        new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE));

    private Map<String, UClass> mCandidates = new HashMap<>();

    @Override
    public void beforeCheckProject(Context context) {
        mCandidates.clear();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(Context context) {
        final JavaEvaluator evaluator = context.getEvaluator();
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.isAbstract() || node.isInterface() || node.isEnum() || node.isAnnotationType()) {
                    return;
                }
                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }

                if (evaluator.extendsClass(node, "android.app.Activity", false) ||
                    evaluator.extendsClass(node, "android.app.Service", false) ||
                    evaluator.extendsClass(node, "android.content.ContentProvider", false) ||
                    evaluator.extendsClass(node, "android.content.BroadcastReceiver", false)) {
                    mCandidates.put(qualifiedName, node);
                }
            }
        };
    }

    @Override
    public void afterCheckProject(Context context) {
        if (mCandidates.isEmpty()) {
            return;
        }

        File manifest = context.getProject().getManifest();
        Set<String> registered = new HashSet<>();
        String pkg = null;

        if (manifest != null && manifest.exists()) {
            try {
                Document document = new XmlParser().parse(manifest);
                Element root = document.getDocumentElement();
                pkg = root.getAttribute("package");
                if (pkg == null) pkg = "";

                NodeList applications = root.getElementsByTagName("application");
                if (applications.getLength() > 0) {
                    Element app = (Element) applications.item(0);
                    NodeList children = app.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Node.ELEMENT_NODE) {
                            String tag = child.getNodeName();
                            if (tag.equals("activity") || tag.equals("service") ||
                                tag.equals("provider") || tag.equals("receiver") ||
                                tag.equals("activity-alias")) {
                                String name = ((Element) child).getAttributeNS(SdkConstants.ANDROID_URI, "name");
                                if (name != null && !name.isEmpty()) {
                                    if (name.startsWith(".")) {
                                        name = pkg + name;
                                    } else if (name.indexOf('.') == -1) {
                                        name = pkg + "." + name;
                                    }
                                    registered.add(name);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore manifest parsing errors
            }
        }

        for (Map.Entry<String, UClass> entry : mCandidates.entrySet()) {
            if (!registered.contains(entry.getKey())) {
                UClass node = entry.getValue();
                context.report(ISSUE, node, context.getLocation(node),
                    "Class is not registered in the manifest");
            }
        }
        mCandidates.clear();
    }
}