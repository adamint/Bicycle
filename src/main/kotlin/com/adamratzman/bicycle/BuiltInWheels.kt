package com.adamratzman.bicycle

class IfWheel : WheelFunctionBlock("if", listOf(WheelArgument("value", listOf(ParameterType.BOOLEAN), true))) {
    override fun shouldRun(
        arguments: WheelValueMap,
        setVariables: WheelValueMap,
        innerTemplate: BicycleTemplate,
        context: BicycleContext
    ): Boolean {
        val value = arguments["value"] as? Boolean
        return if (value is Boolean) value else value != null
    }
}

class NotWheel : WheelFunctionBlock("not", listOf(WheelArgument("value", listOf(ParameterType.BOOLEAN), true))) {
    override fun shouldRun(
        arguments: WheelValueMap,
        setVariables: WheelValueMap,
        innerTemplate: BicycleTemplate,
        context: BicycleContext
    ): Boolean {
        val value = arguments["value"] as? Boolean
        return if (value is Boolean) !value else value == null
    }
}

class WithWheel : WheelFunctionBlock("with", listOf(WheelArgument("value", listOf(ParameterType.OBJECT), true))) {
    override fun shouldRun(
        arguments: WheelValueMap,
        setVariables: WheelValueMap,
        innerTemplate: BicycleTemplate,
        context: BicycleContext
    ): Boolean {
        return arguments["value"] != null
    }

    override fun render(arguments: WheelValueMap, innerTemplate: BicycleTemplate, context: BicycleContext): String {
        val value = (arguments["value"] as Any)
        val newVariables =
            BicycleContext(value.javaClass.declaredFields.map {
                it.isAccessible = true; it.name to it.get(value)
            }.toMap())
        val newContext = context + newVariables
        return innerTemplate.render(newContext)
    }

}

class EachWheel : WheelFunctionBlock("each", listOf(WheelArgument("value", listOf(ParameterType.LIST), true))) {
    override fun shouldRun(
        arguments: WheelValueMap,
        setVariables: WheelValueMap,
        innerTemplate: BicycleTemplate,
        context: BicycleContext
    ): Boolean {
        return arguments["value"] != null
    }

    override fun render(arguments: WheelValueMap, innerTemplate: BicycleTemplate, context: BicycleContext): String {
        val iterable = (arguments["value"] as? Array<*>)?.asIterable() ?: (arguments["value"] as List<*>)
        return iterable.joinToString(lineSeparator) { value ->
            val newVariables = BicycleContext(value?.let { _ ->
                value.javaClass.declaredFields.map { it.isAccessible = true; it.name to it.get(value) }.toMap()
            } ?: mapOf())
            val newContext = context + newVariables
            innerTemplate.render(newContext)
        }
    }
}

class TemplateResolverWheel :
    WheelVariableBlock("template-resolver", listOf(WheelArgument("value", listOf(ParameterType.OBJECT), false))) {
    override fun render(
        arguments: WheelValueMap,
        setVariables: WheelValueMap,
        context: BicycleContext
    ): Any? {
        val value = arguments["value"] as? String
        return if (value is String) (context.values["engine"] as BicycleEngine).templates[value]?.render(context)
            ?: throw IllegalArgumentException("Unknown template referenced ($value)") else null
    }
}

class EqualsWheel : WheelFunctionBlock(
    "equals", listOf(
        WheelArgument("first", listOf(ParameterType.OBJECT), true),
        WheelArgument("second", listOf(ParameterType.OBJECT), true)
    )
) {
    override fun shouldRun(
        arguments: WheelValueMap,
        setVariables: WheelValueMap,
        innerTemplate: BicycleTemplate,
        context: BicycleContext
    ): Boolean {
        return arguments["first"] == arguments["second"]
    }
}

class VariableResolverWheel : WheelVariableBlock(
    "variable-resolver", listOf(
        WheelArgument("value", listOf(ParameterType.OBJECT), true),
        WheelArgument("show-null", listOf(ParameterType.BOOLEAN), true),
        WheelArgument("allow-function-invocation", listOf(ParameterType.BOOLEAN), true)
    )
) {
    override fun render(arguments: WheelValueMap, setVariables: WheelValueMap, context: BicycleContext): Any? {
        val value = arguments["value"]
        val resolvedObject =
            if (arguments["value"] !is String) arguments["value"]
            else if (value is String && value.startsWith('"') && value.endsWith('"')) value.substring(
                1,
                value.lastIndex
            )
            else (value as String).let { _ ->
                val contextVariables = context.values
                if (contextVariables.containsKey(value) /* flattened json key */) contextVariables[value]
                else {
                    val lastIndex = if (value.indexOf('.') == -1) value.length else value.indexOf('.')
                    var currentObject: Any? = contextVariables[value.substring(0, lastIndex)]
                    value.substring(value.indexOf('.') + 1, value.length).split(".").forEach { name ->
                        try {
                            currentObject?.let {
                                val field = it.javaClass.getDeclaredField(name)
                                field.isAccessible = true
                                currentObject = field.get(it)
                            }
                        } catch (e: Exception) {
                            if (setVariables["allow-function-invocation"] == true)
                                try {
                                    if (name.indexOf('(') != -1 && name.indexOf(')') != -1) {
                                        val methodName = name.substring(0, name.indexOf('('))
                                        currentObject?.let { o ->
                                            val methodParameters =
                                                name.substring(name.indexOf('(') + 1, name.lastIndexOf(')')).split(",")
                                                    .filter { it.isNotBlank() }.map { it.trim() }.map { param ->
                                                        render(
                                                            WheelValueMap(mapOf("value" to param)),
                                                            setVariables,
                                                            context
                                                        )
                                                    }.toTypedArray()

                                            val method =
                                                o.javaClass.declaredMethods.first {
                                                    it.name == methodName && it.parameterCount == methodParameters.size
                                                }
                                            method.isAccessible = true
                                            currentObject = if (methodParameters.isEmpty()) method.invoke(o)
                                            else method.invoke(o, *methodParameters)
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (e is IllegalArgumentException) throw BicycleException(
                                        "Invalid types of parameters in '$value'",
                                        e
                                    )
                                    currentObject = null
                                }
                        }
                    }
                    currentObject
                }
            }
        val showNull = (arguments["show-null"] as? Boolean) ?: false
        return resolvedObject ?: if (showNull) "null" else null
    }
}