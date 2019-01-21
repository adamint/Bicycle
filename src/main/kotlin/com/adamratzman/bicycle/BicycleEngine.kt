package com.adamratzman.bicycle

import java.io.File

internal val lineSeparator = System.getProperty("line.separator")

class BicycleException(message: String, cause: Throwable? = null) : Exception(message, cause)

class BicycleEngine(var globalContext: BicycleContext? = null) {
    val wheels = mutableListOf(
        IfWheel(),
        NotWheel(),
        EqualsWheel(),
        VariableResolverWheel(),
        TemplateResolverWheel(),
        EachWheel(),
        WithWheel()
    )
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
}
