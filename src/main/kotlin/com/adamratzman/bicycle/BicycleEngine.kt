package com.adamratzman.bicycle

import java.io.File

internal val lineSeparator = System.getProperty("line.separator")

class BicycleException(message: String, cause: Throwable? = null) : Exception(message, cause)

class BicycleEngine(var globalContext: BicycleContext? = null) {
    private val wheels = mutableListOf(
        IfWheel(),
        NotWheel(),
        EqualsWheel(),
        VariableResolverWheel(),
        TemplateResolverWheel(),
        EachWheel(),
        WithWheel()
    )

    val allWheels get() = wheels.sortedByDescending { it.name.length }

    val templates = mutableMapOf<String, BicycleTemplate>()
    private val parser = BicycleTemplateParser(this)

    fun render(templateName: String, context: BicycleContext): String {
        return templates[templateName]?.render(globalContext?.let { it + context } ?: context)
            ?: throw IllegalArgumentException("No template by the name '$templateName' found")
    }

    fun compileResourcesDirectory(path: String) = File(javaClass.classLoader.getResource(path).file).let { directory ->
        directory.walkTopDown().filter { it.isFile }
            .forEach {
                compile(
                    it.path.removePrefix(directory.path).replace(File.separatorChar, '/').trimStart('/'),
                    it.readText()
                )
            }
    }

    fun compile(templateName: String, templateString: String) {
        if (templates.containsKey(templateName)) throw BicycleException("A template named '$templateName' already exists")
        wheels.sortByDescending { it.name.length }
        templates[templateName] = parser.parse(templateString)
    }

    fun removeWheel(name: String) = wheels.removeIf { it.name == name }

    fun addWheel(wheel: Wheel) {
        if (wheels.find { it.name == wheel.name } != null) throw IllegalArgumentException("Wheel with the name ${wheel.name} already exists")
        wheels.add(wheel)
    }

    fun addVariableWheel(
        name: String, vararg arguments: WheelArgument,
        render: (WheelValueMap, WheelValueMap, BicycleContext) -> Any?
    ) {
        addWheel(object : WheelVariableBlock(name, arguments.toList()) {
            override fun render(
                arguments: WheelValueMap,
                setVariables: WheelValueMap,
                context: BicycleContext
            ) = render.invoke(arguments, setVariables, context)
        })
    }

    fun addFunctionWheel(
        name: String, vararg arguments: WheelArgument,
        shouldRun: (WheelValueMap, WheelValueMap, BicycleTemplate, BicycleContext) -> Boolean,
        render: ((WheelValueMap, BicycleTemplate, BicycleContext) -> String)? = null
    ) {
        addWheel(object : WheelFunctionBlock(name, arguments.toList()) {
            override fun shouldRun(
                arguments: WheelValueMap,
                setVariables: WheelValueMap,
                innerTemplate: BicycleTemplate,
                context: BicycleContext
            ) = shouldRun(arguments, setVariables, innerTemplate, context)

            override fun render(
                arguments: WheelValueMap,
                innerTemplate: BicycleTemplate,
                context: BicycleContext
            ) = render?.let { it(arguments, innerTemplate, context) } ?: super.render(arguments, innerTemplate, context)
        })
    }
}
