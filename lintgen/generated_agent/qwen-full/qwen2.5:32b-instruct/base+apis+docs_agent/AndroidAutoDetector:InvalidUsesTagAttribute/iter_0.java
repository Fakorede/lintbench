package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("automotiveApp".equals(element.getParentNode().getNodeName())) {
            Attr nameAttr = element.getAttributeNode("name");
            if (nameAttr != null) {
                String nameValue = nameAttr.getValue();
                if (!"media".equals(nameValue) && !"notification".equals(nameValue) && !"sms".equals(nameValue)) {
                    context.report(
                            this,
                            element,
                            context.getLocation(element),
                            "Invalid value for 'name' attribute in <uses> element. Valid values are 'media', 'notification', or 'sms'."
                    );
                }
            } else {
                context.report(
                        this,
                        element,
                        context.getLocation(element),
                        "'name' attribute is missing in <uses> element."
                );
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }
}