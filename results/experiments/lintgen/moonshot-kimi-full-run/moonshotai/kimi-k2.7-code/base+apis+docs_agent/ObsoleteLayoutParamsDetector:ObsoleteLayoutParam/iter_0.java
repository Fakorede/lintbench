package com.android.tools.lint.checks;

import static com.android.SdkConstants.*;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.*;

public class ObsoleteLayoutParamsDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ObsoleteLayoutParamsDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no effect. ...",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) return;
        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        Set<String> allowed = LAYOUT_PARAMS.get(parentTag);
        if (allowed == null) return; // unknown layout, skip

        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0, n = attrs.getLength(); i < n; i++) {
            Node attr = attrs.item(i);
            String name = attr.getLocalName();
            String ns = attr.getNamespaceURI();
            if (name == null) continue;
            if (!name.startsWith("layout_")) continue;
            // Only check Android namespace and app namespace (support libs)
            if (!ANDROID_URI.equals(ns) && !APP_URI.equals(ns) && !AUTO_URI.equals(ns)) continue;
            if (!allowed.contains(name)) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        String.format("Invalid layout param in a `%1$s` parent: `%2$s`", parentTag, name));
            }
        }
    }