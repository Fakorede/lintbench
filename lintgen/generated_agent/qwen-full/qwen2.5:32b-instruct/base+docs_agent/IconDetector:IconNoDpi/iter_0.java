package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class IconDetector extends ResourceXmlScanner {

    private Multimap<String, Pair<ResourceFolderType, Density>> iconMap = HashMultimap.create();

    @NonNull
    @Override
    public String[] getsupportedIssues() {
        return new String[]{IconIssue.getCanonicalName()};
    }

    @NonNull
    @Override
    public ResourceFolderType[] getApplicableResourceTypes() {
        return new ResourceFolderType[]{
                ResourceFolderType.DRAWABLE,
                ResourceFolderType.DRAWABLE_NODPI
        };
    }

    @Override
    public void visitDocument(@NonNull Document document, @NonNull XmlContext context) throws SAXException, IOException {
        Element root = document.getDocumentElement();
        if ("resources".equals(root.getTagName())) {
            for (Element item : getAllChildren(root)) {
                String name = item.getAttribute("name");
                ResourceFolderType folderType = context.getResourceFolderType();
                Density density = context.getDensity();

                iconMap.put(name, Pair.of(folderType, density));
            }
        }
    }

    @Override
    public void afterCheckFiles(@NonNull Context context) {
        Set<String> ambiguousIcons = new HashSet<>();
        for (String name : iconMap.keySet()) {
            if (iconMap.get(name).size() > 1) {
                boolean hasNodpi = false;
                boolean hasDpi = false;

                for (Pair<ResourceFolderType, Density> pair : iconMap.get(name)) {
                    ResourceFolderType folderType = pair.getFirst();
                    Density density = pair.getSecond();

                    if (folderType == ResourceFolderType.DRAWABLE_NODPI) {
                        hasNodpi = true;
                    } else if (density != null && !Density.isUndefined(density)) {
                        hasDpi = true;
                    }

                    if (hasNodpi && hasDpi) {
                        ambiguousIcons.add(name);
                        break;
                    }
                }
            }
        }

        for (String name : ambiguousIcons) {
            context.report(IconIssue, context.getLocation(iconMap.get(name).iterator().next()), "Icon appears in both -nodpi and dpi folders");
        }
    }

    private static Element[] getAllChildren(Element element) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElement("root");
            for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                Node node = element.getChildNodes().item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    root.appendChild(doc.importNode(node, true));
                }
            }

            return new Element[]{root};
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static final Issue IconIssue = Issue.create(
            "AmbiguousIcon",
            "Bitmaps that appear in drawable-nodpi folders will not be scaled by the Android framework. If a drawable resource of the same name appears both in a -nodpi folder as well as a dpi folder such as drawable-hdpi, then the behavior is ambiguous and probably not intentional.",
            "Delete one or the other, or use different names for the icons.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );
}