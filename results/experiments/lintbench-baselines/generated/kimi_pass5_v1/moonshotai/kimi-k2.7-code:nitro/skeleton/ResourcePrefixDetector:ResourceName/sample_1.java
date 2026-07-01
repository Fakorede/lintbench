package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    private static final String TAG_RESOURCES = "resources";
    private static final String TAG_DECLARE_STYLEABLE = "declare-styleable";
    private static final String ATTR_NAME = "name";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    ResourcePrefixDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure "
                            + "that you don't accidentally combine resources from different "
                            + "libraries, since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private String mPrefix;

    public ResourcePrefixDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_RESOURCES);
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mPrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (!isActive()) {
            return;
        }

        File file = context.file;
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String folderName = parent.getName();
        if (folderName.startsWith("values")) {
            // Values files contain multiple named resources; their names are checked
            // by the XML element visitor.
            return;
        }

        String name = getBaseName(file);
        if (!name.startsWith(mPrefix)) {
            reportViolation(context, file, name);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isActive() || !TAG_RESOURCES.equals(element.getTagName())) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element child = (Element) node;
            checkResourceName(context, child);

            if (TAG_DECLARE_STYLEABLE.equals(child.getTagName())) {
                NodeList attrs = child.getChildNodes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    Node attrNode = attrs.item(j);
                    if (attrNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    checkResourceName(context, (Element) attrNode);
                }
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (!isActive()) {
            return;
        }

        File file = context.file;
        String name = getBaseName(file);
        if (!name.startsWith(mPrefix)) {
            reportViolation(context, file, name);
        }
    }

    private boolean isActive() {
        return mPrefix != null && !mPrefix.isEmpty();
    }

    private void checkResourceName(XmlContext context, Element element) {
        if (!element.hasAttribute(ATTR_NAME)) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty() || name.startsWith(mPrefix)) {
            return;
        }

        Location location = context.getNameLocation(element);
        context.report(
                ISSUE,
                element,
                location,
                String.format(
                        "Resource named `%1$s` does not start with the project's resource prefix `%2$s`",
                        name, mPrefix));
    }

    private void reportViolation(Context context, File file, String name) {
        context.report(
                ISSUE,
                Location.create(file),
                String.format(
                        "Resource named `%1$s` does not start with the project's resource prefix `%2$s`",
                        name, mPrefix));
    }

    private static String getBaseName(File file) {
        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}