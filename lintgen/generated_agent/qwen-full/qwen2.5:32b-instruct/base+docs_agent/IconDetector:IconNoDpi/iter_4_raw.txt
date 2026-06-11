package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class IconDetector extends ResourceXmlDetector {

    private Set<String> iconNames = new HashSet<>();

    @NonNull
    public static final Issue ICON_ISSUE = Issue.create(
            "AmbiguousIcon",
            "Bitmaps that appear in drawable-nodpi folders will not be scaled by the Android framework. If a drawable resource of the same name appears both in a -nodpi folder as well as a dpi folder such as drawable-hdpi, then the behavior is ambiguous and probably not intentional.",
            "Delete one or the other, or use different names for the icons.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitResource(@NonNull Context context, @NonNull ResourceFolderType folderType, @NonNull Document document) throws SAXException {
        Element root = document.getDocumentElement();
        if ("resources".equals(root.getTagName())) {
            for (int i = 0; i < root.getChildNodes().getLength(); i++) {
                org.w3c.dom.Node node = root.getChildNodes().item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    Element item = (Element) node;
                    String name = item.getAttribute("name");

                    if (!iconNames.contains(name)) {
                        iconNames.add(name);
                        if (isAmbiguousIcon(name, folderType, context)) {
                            Location location = Location.create(context.getDriver(), context.getProject(), document, item);
                            context.report(ICON_ISSUE, location, "Icon appears in both -nodpi and dpi folders");
                        }
                    }
                }
            }
        }
    }

    private boolean isAmbiguousIcon(String name, ResourceFolderType folderType, Context context) {
        if (folderType == ResourceFolderType.DRAWABLE && hasDpiVersion(name, context)) {
            return true;
        } else if (!ResourceFolderType.isUndefined(folderType.getDefaultDensity()) && hasNodpiVersion(name, context)) {
            return true;
        }
        return false;
    }

    private boolean hasDpiVersion(String name, Context context) {
        for (ResourceFolderType type : ResourceFolderType.values()) {
            if (type != ResourceFolderType.DRAWABLE_NODPI && !ResourceFolderType.isUndefined(type.getDefaultDensity())) {
                if (context.findResource(name, type) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasNodpiVersion(String name, Context context) {
        for (ResourceFolderType type : ResourceFolderType.values()) {
            if (type == ResourceFolderType.DRAWABLE_NODPI && context.findResource(name, type) != null) {
                return true;
            }
        }
        return false;
    }
}