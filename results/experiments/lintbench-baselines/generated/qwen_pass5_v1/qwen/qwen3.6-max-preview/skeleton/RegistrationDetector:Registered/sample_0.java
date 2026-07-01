package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the "
                            + "`AndroidManifest.xml` file using `<activity>`, `<service>` and "
                            + "`<provider>` tags.\n\n"
                            + "If your activity is simply a parent class intended to be "
                            + "subclassed by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private final List<UClass> mClasses = new ArrayList<>();
    private final List<JavaContext> mContexts = new ArrayList<>();

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider",
                "android.content.BroadcastReceiver",
                "android.app.Application");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }
        String name = declaration.getName();
        if (name == null) {
            return;
        }
        mClasses.add(declaration);
        mContexts.add(context);
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        if (mClasses.isEmpty()) {
            return;
        }

        File manifest = context.getProject().getManifest();
        Set<String> registered = new HashSet<>();

        if (manifest != null && manifest.exists()) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(manifest);
                Element root = doc.getDocumentElement();
                String pkg = root.getAttribute("package");

                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node node = children.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        String tag = node.getNodeName();
                        if ("application".equals(tag)) {
                            Element appElem = (Element) node;
                            String appName = appElem.getAttributeNS(ANDROID_NS, "name");
                            if (appName != null && !appName.isEmpty()) {
                                registered.add(resolveClassName(pkg, appName));
                            }

                            NodeList appChildren = appElem.getChildNodes();
                            for (int j = 0; j < appChildren.getLength(); j++) {
                                Node child = appChildren.item(j);
                                if (child.getNodeType() == Node.ELEMENT_NODE) {
                                    String childTag = child.getNodeName();
                                    if ("activity".equals(childTag) || "service".equals(childTag)
                                            || "provider".equals(childTag) || "receiver".equals(childTag)) {
                                        String compName = ((Element) child).getAttributeNS(ANDROID_NS, "name");
                                        if (compName != null && !compName.isEmpty()) {
                                            registered.add(resolveClassName(pkg, compName));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // If manifest parsing fails, skip registration check to avoid false positives
            }
        }

        for (int i = 0; i < mClasses.size(); i++) {
            UClass cls = mClasses.get(i);
            JavaContext ctx = mContexts.get(i);
            String fqn = cls.getQualifiedName();
            if (fqn != null && !registered.contains(fqn)) {
                ctx.report(ISSUE, ctx.getLocation(cls),
                        "Class is not registered in the manifest");
            }
        }

        mClasses.clear();
        mContexts.clear();
    }

    private static String resolveClassName(String pkg, String name) {
        if (name.startsWith(".")) {
            return pkg + name;
        }
        if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }
}