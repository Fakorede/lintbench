override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "merge") {
            val parentTag = element.getAttributeNS(SdkConstants.TOOLS_URI, "parentTag")
            if (parentTag != "androidx.constraintlayout.widget.ConstraintLayout" &&
                parentTag != "android.support.constraint.ConstraintLayout") {
                return
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                checkChild(context, child)
            }
        }
    }