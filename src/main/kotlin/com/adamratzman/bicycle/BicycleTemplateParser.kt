package com.adamratzman.bicycle

class BicycleTemplateParser(val engine: BicycleEngine) {
    fun parse(templateString: String): BicycleTemplate {
        val templateSkeletons = mutableListOf<BicycleTemplateSkeleton>()

        var temporaryString = templateString
        while (temporaryString.indexOf("{{") != -1) {
            val left = firstIndexOf(temporaryString, "{{", '\\')
            if (left == -1) {
                return BicycleTemplate(engine, templateSkeletons + BicycleTextSkeleton(temporaryString))
            }
            if (left > 0) {
                templateSkeletons.add(BicycleTextSkeleton(temporaryString.substring(0, left)))
                temporaryString = temporaryString.substring(left)
                continue
            }
            val right = firstIndexOf(temporaryString, "}}", '\\')
            if (right == -1 || left > right) throw IllegalArgumentException("Wheel beginning but no end")

            var wheelDefinitionString = temporaryString.substring(left + 2, right).trim()

            var raw = false

            val wheel = when {
                wheelDefinitionString.startsWith('#') -> engine.wheels.find {
                    it.name == wheelDefinitionString.substring(
                        1, wheelDefinitionString.indexOf(' ')
                    )
                }?.apply {
                    wheelDefinitionString = wheelDefinitionString.substring(wheelDefinitionString.indexOf(' ') + 1)
                } ?: throw IllegalArgumentException("No wheel found in {{$wheelDefinitionString}}")
                wheelDefinitionString.startsWith(">") -> engine.wheels.first { it is TemplateResolverWheel }
                    .apply { wheelDefinitionString = wheelDefinitionString.removePrefix(">") }

                else -> {
                    val split = wheelDefinitionString.removePrefix("&").trim().split(" ")
                    val found = engine.wheels.firstOrNull { it.name == split[0] }
                    if (found != null) {
                        if (found is WheelVariableBlock && wheelDefinitionString.startsWith("&")) raw = true
                        wheelDefinitionString = split.subList(1, split.size).joinToString(" ")
                        found
                    } else engine.wheels.first { it is VariableResolverWheel }.apply {
                        if (this is WheelVariableBlock && wheelDefinitionString.startsWith("&")) raw = true
                        wheelDefinitionString = split.joinToString(" ")
                    }
                }
            }

            wheelDefinitionString = wheelDefinitionString.trim()

            val (arguments, setVariables) = parseWheelDefinition(wheel, wheelDefinitionString)
            if (raw) setVariables["noescape"] = true

            if (wheel is WheelVariableBlock) {
                templateSkeletons.add(BicycleWheelSkeleton(engine, wheel, null, arguments, WheelValueMap(setVariables)))
                temporaryString = temporaryString.substring(right + 2)
            } else {
                val sameWheelStartIndices = allIndexOf(temporaryString, "\\{\\{.*${wheel.name}.+}}".toRegex())
                val sameWheelEndIndices = allIndexOf(temporaryString, "\\{\\{/${wheel.name}}}".toRegex())

                val wheelEndBlock = sameWheelEndIndices.withIndex().firstOrNull { (index, value) ->
                    index + 1 == sameWheelStartIndices.filter { it < value }.count()
                }?.value
                    ?: throw IllegalArgumentException("Wheel end block needed for $temporaryString (${wheel.name})")

                //println("End index: $wheelEndBlock\nTemplate:\n$temporaryString")

                /*val wheelEndBlock =
                    if (temporaryString.lastIndexOf("{{/${wheel.name}}}") == -1)
                        temporaryString.lastIndexOf("{{ /${wheel.name} }}")
                    else temporaryString.lastIndexOf("{{/${wheel.name}}}")
                throw IllegalArgumentException("Wheel end block needed for $temporaryString (${wheel.name})")
                if (wheelEndBlock == -1) throw IllegalArgumentException("Wheel end block needed for $temporaryString (${wheel.name})") */

                val innerTemplate = parse(
                    temporaryString.substring(right + 2, wheelEndBlock)
                        .removePrefix(lineSeparator)
                        .removeSuffix(lineSeparator)
                )

                templateSkeletons.add(
                    BicycleWheelSkeleton(
                        engine,
                        wheel,
                        innerTemplate,
                        arguments,
                        WheelValueMap(setVariables)
                    )
                )

                temporaryString = temporaryString.substring(0, left) +
                        temporaryString.substring(temporaryString.indexOf("}}", wheelEndBlock) + 2)
            }
        }

        return BicycleTemplate(engine, templateSkeletons + BicycleTextSkeleton(temporaryString))
    }

    private fun firstIndexOf(string: String, delimeter: String, avoidBefore: Char, start: Int = 0): Int {
        val index = string.indexOf(delimeter, start)
        if (index < 1) return index
        if (string[index - 1] != avoidBefore) return index
        return firstIndexOf(string, delimeter, avoidBefore, index + 1)
    }

    private fun allIndexOf(string: String, delimeter: Regex): List<Int> {
        return delimeter.findAll(string).map { it.range.first }.toList()
    }

    fun parseWheelDefinition(wheel: Wheel, string: String): Pair<MutableMap<String, Any?>, MutableMap<String, Any?>> {
        var pivotalEqualsIndex = -1
        string.let { _ ->
            var quote = false
            for ((i, char) in string.withIndex()) {
                if (string.getOrNull(i - 1) != '\\' && char == '"' && !quote) quote = true
                else if (string.getOrNull(i - 1) != '\\' && char == '"' && quote) quote = false
                else if (char == '=' && !quote) {
                    pivotalEqualsIndex = i
                    break
                }
            }
        }

        // no assignments
        if (pivotalEqualsIndex == -1) return Pair(transformArguments(wheel, parseArguments(string), string), mutableMapOf())

        val delinationIndex = string.substring(0, pivotalEqualsIndex).lastIndexOf(' ')

        // no arguments
        if (delinationIndex == -1) return Pair(mutableMapOf(), parseAssignments(string))

        val argumentsString = string.substring(0, delinationIndex)
        val assignmentsString = string.substring(delinationIndex + 1)

        val arguments = transformArguments(wheel, parseArguments(argumentsString), string).toMutableMap()
        val assignments = parseAssignments(assignmentsString).toMutableMap()

        assignments.filter { (key) ->
            wheel.possibleArguments.find { it.name == key } != null
        }.forEach { key, value ->
            assignments.remove(key)
            arguments[wheel.possibleArguments.first { it.name == key }.name] = value
        }

        return Pair(arguments, assignments)
    }

    fun transformArguments(wheel: Wheel, list: List<Any?>, contextString: String) = list.mapIndexed { i, value ->
        wheel.possibleArguments.getOrNull(i)?.let { it.name to value }
            ?: throw IllegalArgumentException("Unknown argument $value in position $i ($contextString)")
    }.toMap().toMutableMap()

    fun parseArguments(string: String): List<Any?> {
        val objects = mutableListOf<Any?>()
        var mutable = string.trim()
        while (mutable.isNotBlank()) {
            if (mutable.startsWith('"')) {
                val endQuoteIndex = firstIndexOf(mutable, "\"", '\\', 1)
                if (endQuoteIndex == -1) throw IllegalArgumentException("String not closed: $string")
                objects.add(mutable.substring(0, endQuoteIndex + 1).replace("\\\"", "\""))
                mutable =
                        if (endQuoteIndex == mutable.lastIndex) ""
                        else mutable.substring(endQuoteIndex + 1).trimStart()
            } else {
                val spaceIndex = mutable.indexOf(' ')
                mutable = if (spaceIndex == -1) {
                    objects.add(cast(mutable))
                    ""
                } else {
                    objects.add(cast(mutable.substring(0, spaceIndex)))
                    mutable.substring(spaceIndex + 1)
                }
            }
        }
        return objects
    }

    fun parseAssignments(string: String): MutableMap<String, Any?> {
        val objects = mutableMapOf<String, Any?>()
        var mutable = string.trim()
        while (mutable.isNotBlank()) {
            val equals = mutable.indexOf('=')
            if (equals == -1) throw IllegalArgumentException("Assignment has no equals sign ($mutable)")
            val name = mutable.substring(0, equals)
            if (name.toIntOrNull() != null) throw IllegalArgumentException("Assignment name ($name) cannot be an integer")
            val valueStart = equals + 1
            if (mutable[valueStart] == '"') {
                val endQuote = firstIndexOf(mutable, "\"", '\\', valueStart + 1)
                if (endQuote == -1) throw IllegalArgumentException("Quote has no end ($mutable)")
                objects[name] = mutable.substring(valueStart, endQuote + 1)
                mutable = if (endQuote == mutable.lastIndex) "" else mutable.substring(endQuote + 1).trimStart()
            } else {
                val spaceIndex = mutable.indexOf(' ')
                if (spaceIndex == -1) {
                    objects[name] = cast(mutable.substring(valueStart))
                    mutable = ""
                } else {
                    objects[name] = cast(mutable.substring(valueStart, spaceIndex))
                    mutable = mutable.substring(spaceIndex + 1)
                }
            }
        }

        return objects
    }
}

internal fun cast(string: String): Any? {
    return when {
        string == "null" -> null
        string.toIntOrNull() != null -> string.toInt()
        string.toFloatOrNull() != null -> string.toFloat()
        string.toDoubleOrNull() != null -> string.toDouble()
        string.toLongOrNull() != null -> string.toLong()
        string == "false" || string == "true" -> string.toBoolean()
        else -> string
    }
}