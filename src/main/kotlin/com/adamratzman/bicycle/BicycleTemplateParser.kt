package com.adamratzman.bicycle

class BicycleTemplateParser(val engine: BicycleEngine) {
    fun parse(templateString: String): BicycleTemplate {
        val templateSkeletons = mutableListOf<BicycleTemplateSkeleton>()

        var temporaryString = templateString
        while (temporaryString.indexOf("{{") != -1) {
            val left = firstIndexOf(temporaryString, "{{", '\\')
            if (left == -1) {
                return BicycleTemplate(engine,templateSkeletons + BicycleTextSkeleton(temporaryString))
            }
            if (left > 0) {
                templateSkeletons.add(BicycleTextSkeleton(temporaryString.substring(0, left)))
                temporaryString = temporaryString.substring(left)
                continue
            }
            val right = firstIndexOf(temporaryString, "}}", '\\')
            if (right == -1 || left > right) throw IllegalArgumentException("Wheel beginning but no end")

            var wheelDefinitionString = temporaryString.substring(left + 2, right).trim()

            // find wheel

            val wheel = when {
                wheelDefinitionString.indexOf(' ') == -1 -> engine.wheels.first { it.name == "" }
                wheelDefinitionString.startsWith('#') -> engine.wheels.find {
                    it.name == wheelDefinitionString.substring(
                        1, wheelDefinitionString.indexOf(' ')
                    )
                }?.apply {
                    wheelDefinitionString = wheelDefinitionString.substring(wheelDefinitionString.indexOf(' ') + 1)
                } ?: throw IllegalArgumentException("No wheel found in {{$wheelDefinitionString}}")
                wheelDefinitionString.startsWith(">") -> engine.wheels.first { it.name == "template-resolver" }
                    .apply { wheelDefinitionString.removePrefix(">") }

                else -> engine.wheels.first { it.name == "" }
            }

            // find arguments and set variables
            val arguments = mutableListOf<Pair<String?, Any?>>()
            val setVariables = mutableMapOf<String, Any?>()

            var start = 0
            var index = 0
            var quoteLeft: Int? = null
            var lastSpace: Int? = null

            val lastIndex = if (wheelDefinitionString.indexOf('=') == -1) wheelDefinitionString.length
            else (wheelDefinitionString.indexOf('=')..0).firstOrNull { wheelDefinitionString[it] == ' ' } ?: 0

            while (index < lastIndex) {
                val char = wheelDefinitionString[index]
                if (quoteLeft == null && char == '"') quoteLeft = index
                else if (quoteLeft != null && char == '"') {
                    arguments.add(null to wheelDefinitionString.substring(quoteLeft + 1, index))
                    quoteLeft = null
                    start = index + 1
                } else if (quoteLeft == null && char == ' ') {
                    arguments.add(null to cast(wheelDefinitionString.substring(lastSpace?.let { it + 1 } ?: 0, index)))
                    start = index + 1
                } else if (index == lastIndex - 1) {
                    arguments.add(null to cast(wheelDefinitionString.substring(start)))
                }
                if (char == ' ') lastSpace = index
                index++
            }

            if (wheelDefinitionString.lastIndex > lastIndex) {
                // find variable setters & named arguments
                wheelDefinitionString = wheelDefinitionString.substring(lastIndex + 1).trim()
                while (wheelDefinitionString.isNotEmpty()) {
                    val equalsIndex = wheelDefinitionString.indexOf('=')
                    val value =
                        if (equalsIndex > 0 && equalsIndex != wheelDefinitionString.lastIndex) {
                            if (wheelDefinitionString[equalsIndex + 1] == '"') {
                                if (wheelDefinitionString.indexOf(' ', equalsIndex + 2) != -1) {
                                    val end = wheelDefinitionString.indexOf(' ', equalsIndex + 2)
                                    wheelDefinitionString.substring(equalsIndex + 1, end).apply {
                                        wheelDefinitionString = wheelDefinitionString.substring(end + 1)
                                    }
                                } else throw IllegalArgumentException("Unclosed quote: $wheelDefinitionString")
                            } else if (wheelDefinitionString.indexOf(' ') == -1) {
                                wheelDefinitionString.substring(equalsIndex + 1).apply {
                                    wheelDefinitionString = ""
                                }
                            } else {
                                val end = wheelDefinitionString.indexOf(' ')
                                wheelDefinitionString.substring(equalsIndex + 1, end).apply {
                                    wheelDefinitionString = wheelDefinitionString.substring(end + 1)
                                }
                            }
                        } else throw IllegalArgumentException("Illegal variable setter: $wheelDefinitionString")
                    val name = wheelDefinitionString.substring(0, equalsIndex)

                    if (name in wheel.possibleArguments.map { it.name }) arguments.add(name to cast(value))
                    else setVariables[name] = cast(value)
                }
            }

            if (wheel is WheelVariableBlock) {
                templateSkeletons.add(BicycleWheelSkeleton(engine,wheel, null, arguments, WheelValueMap(setVariables)))
                temporaryString = temporaryString.substring(right + 2)
            } else {
                val wheelEndBlock =
                    if (temporaryString.lastIndexOf("{{/${wheel.name}}}") == -1)
                        temporaryString.lastIndexOf("{{ /${wheel.name} }}")
                    else temporaryString.lastIndexOf("{{/${wheel.name}}}")
                if (wheelEndBlock == -1) throw IllegalArgumentException("Wheel end block needed for $temporaryString (${wheel.name})")

                val innerTemplate = parse(temporaryString.substring(right + 2, wheelEndBlock))

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

        return BicycleTemplate(engine,templateSkeletons + BicycleTextSkeleton(temporaryString))
    }

    private fun firstIndexOf(string: String, delimeter: String, avoidBefore: Char): Int {
        val index = string.indexOf(delimeter)
        if (index < 1) return index
        if (string[index - 1] != avoidBefore) return index
        return index + firstIndexOf(string.substring(index + 1), delimeter, avoidBefore)
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