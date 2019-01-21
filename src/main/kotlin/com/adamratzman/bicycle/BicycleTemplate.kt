package com.adamratzman.bicycle

import com.adamratzman.bicycle.ParameterType.*

data class BicycleTemplate(val engine: BicycleEngine, val parts: List<BicycleTemplateSkeleton>) {
    fun render(model: Map<String, Any?>) = render(BicycleContext(WheelValueMap(model)))

    fun render(context: BicycleContext): String = parts.joinToString("") { skeleton ->
        when (skeleton) {
            is BicycleTextSkeleton -> skeleton.text
            is BicycleWheelSkeleton -> {
                BicycleWheelConsumer(
                    engine,
                    skeleton.innerTemplate,
                    skeleton.wheel,
                    skeleton.arguments,
                    skeleton.setVariables,
                    context + BicycleContext("engine" to engine)
                ).render()
            }
            else -> skeleton.toString()
        }
    }
}

abstract class BicycleTemplateSkeleton

class BicycleTextSkeleton(val text: String) : BicycleTemplateSkeleton()

class BicycleWheelSkeleton(
    val engine: BicycleEngine,
    val wheel: Wheel,
    val innerTemplate: BicycleTemplate?,
    val arguments: List<Pair<String?, Any?>>,
    val setVariables: WheelValueMap
) : BicycleTemplateSkeleton() {
    fun render(context: BicycleContext) =
        BicycleWheelConsumer(engine, innerTemplate, wheel, arguments, setVariables, context).render()
}

class BicycleWheelConsumer(
    val engine: BicycleEngine,
    val innerTemplate: BicycleTemplate?,
    val wheel: Wheel,
    val arguments: List<Pair<String?, Any?>>,
    val setVariables: WheelValueMap,
    val context: BicycleContext
) {
    fun render(): String {
        if (arguments.size > wheel.possibleArguments.size) throw IllegalArgumentException("Too many arguments specified ($arguments to ${wheel.possibleArguments})")

        val foundArguments = mutableMapOf<WheelArgument, Any?>()

        arguments.toMutableList().forEachIndexed { i, providedArgument ->
            val matched =
                providedArgument.first?.let { arg ->
                    wheel.possibleArguments.find { it.name == arg }?.let { possible ->
                        if (providedArgument.second == null && !possible.nullable) throw IllegalArgumentException("$possible cannot be null (given $providedArgument)")
                        else possible
                    }
                }
                    ?: wheel.possibleArguments.getOrNull(i)
                    ?: throw IllegalArgumentException("No argument found matching the provided argument: $providedArgument")
            if (foundArguments.keys.find { it.name == matched.name } != null) throw IllegalArgumentException("Argument name ${matched.name} found twice")
            else foundArguments[matched] =
                    if (wheel is VariableResolverWheel) providedArgument.second else VariableResolverWheel().render(
                        WheelValueMap(mapOf("value" to providedArgument.second)),
                        setVariables,
                        context
                    )
        }

        val nonNullableNotFoundArguments = wheel.possibleArguments.filter { !it.nullable } - foundArguments.keys
        if (nonNullableNotFoundArguments.isNotEmpty()) {
            throw IllegalArgumentException("Some non-nullable arguments weren't provided: $nonNullableNotFoundArguments")
        }

        foundArguments.forEach { arg, value ->
            val types = arg.takes
            (if (OBJECT in arg.takes) null
            else when (value) {
                is List<*>, is Array<*> -> if (LIST !in arg.takes) "List or Array" else null
                is Number -> {
                    if (NUMBER in arg.takes) null
                    else if (value is Double && DOUBLE !in arg.takes) "A Double"
                    else if (value is Int && INT !in arg.takes) "An Int"
                    else if (value is Float && FLOAT !in arg.takes) "Float"
                    else if (value is Long && LONG !in arg.takes) "Long"
                    else null
                }
                else -> {
                    if (value is String && STRING !in arg.takes) "A String"
                    else if (value is Boolean && BOOLEAN !in arg.takes) "A Boolean"
                    else null
                }
            })?.let { throw IllegalArgumentException("$it was found in $arg ($wheel), but one of $types was required. Value provided: $value") }
        }

        val wheelArguments = WheelValueMap(foundArguments.mapKeys { it.key.name })

        return when (wheel) {
            is WheelVariableBlock -> {
                wheel.renderInternal(WheelValueMap(wheelArguments.filter {
                    it.value !is String || (it.value != "engine" && !(it.value as String).startsWith(
                        "engine."
                    ))
                }), setVariables, context)
            }
            is WheelFunctionBlock -> {
                if (wheel.shouldRun(
                        WheelValueMap(wheelArguments.map {
                            it.key to VariableResolverWheel().render(
                                WheelValueMap(mapOf(it.key to it.value)),
                                setVariables,
                                context
                            )
                        }.toMap()),
                        setVariables,
                        innerTemplate ?: BicycleTemplate(engine, listOf()),
                        context
                    )
                ) {
                    wheel.render(
                        wheelArguments, innerTemplate ?: BicycleTemplate(engine, listOf()), context
                    )
                } else ""
            }
            else -> throw BicycleException("No wheel type matches $wheel")
        }
    }
}

data class BicycleContext(val values: WheelValueMap) {
    constructor(arguments: Map<String, Any?> = mapOf()) : this(WheelValueMap(arguments))
    constructor(vararg arguments: Pair<String, Any?>) : this(arguments.toMap())

    operator fun plus(other: BicycleContext) = BicycleContext(WheelValueMap(values + other.values))
}

abstract class Wheel(val name: String, val possibleArguments: List<WheelArgument>)

abstract class WheelVariableBlock(name: String, possibleArguments: List<WheelArgument>) :
    Wheel(name, possibleArguments) {
    internal fun renderInternal(
        arguments: WheelValueMap,
        setVariables: WheelValueMap,
        context: BicycleContext
    ) = render(arguments, setVariables, context)?.toString() ?: ""

    abstract fun render(
        arguments: WheelValueMap,
        setVariables: WheelValueMap,
        context: BicycleContext
    ): Any?
}

abstract class WheelFunctionBlock(name: String, possibleArguments: List<WheelArgument>) :
    Wheel(name, possibleArguments) {

    abstract fun shouldRun(
        arguments: WheelValueMap,
        setVariables: WheelValueMap,
        innerTemplate: BicycleTemplate,
        context: BicycleContext
    ): Boolean

    open fun render(
        arguments: WheelValueMap,
        innerTemplate: BicycleTemplate,
        context: BicycleContext
    ) = innerTemplate.render(BicycleContext(WheelValueMap(context.values + arguments))).trim()
}

data class WheelArgument(val name: String, val takes: List<ParameterType>, val nullable: Boolean)

enum class ParameterType { BOOLEAN, STRING, NUMBER, INT, DOUBLE, FLOAT, LONG, LIST /* Includes both list and array */, OBJECT }

class WheelValueMap(arguments: Map<String, Any?> = mapOf()) : HashMap<String, Any>(arguments)