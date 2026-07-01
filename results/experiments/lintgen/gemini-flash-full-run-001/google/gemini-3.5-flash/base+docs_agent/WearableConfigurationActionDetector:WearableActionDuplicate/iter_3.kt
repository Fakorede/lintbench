for (activity in activities) {
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val intentFilter = intentFilters.item(j) as Element
                val actions = intentFilter.getElementsByTagName("action")
                var hasAction = false
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    val actionName = action.getAndroidAttribute("name")
                    if (isWatchFaceEditor(actionName)) {
                        hasAction = true
                        break
                    }
                }