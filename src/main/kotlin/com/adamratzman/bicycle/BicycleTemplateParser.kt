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

            val wheel = parseWheel(wheelDefinitionString).let { (wheel, definition) ->
                wheelDefinitionString = definition
                wheel
            }

            wheelDefinitionString = wheelDefinitionString.trim()

            val (arguments, setVariables) = parseWheelDefinition(wheel, wheelDefinitionString)

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


                val innerString = temporaryString.substring(right + 2, wheelEndBlock)
                    .removePrefix(lineSeparator)
                    .removeSuffix(lineSeparator)

                val (innerTemplate, elseTemplate) =
                        if (wheel is IfWheel || wheel is NotWheel) {
                            val start = "\\{\\{#(if|not).+}}".toRegex()
                            val end = "\\{\\{/(if|not)}}".toRegex()

                            val startBlocks = start.findAll(innerString).map { it.range.first }.toList()
                            val endBlocks = end.findAll(innerString).map { it.range.first }.toList()

                            if (startBlocks.size != endBlocks.size) throw IllegalArgumentException("Differing amount of if/not start and end blocks")

                            val ranges =
                                startBlocks.mapIndexed { i, startIndex -> (startIndex..endBlocks[endBlocks.lastIndex - i]) }
                            val elseBlocks =
                                "\\{\\{else}}".toRegex().findAll(innerString).map { it.range.first }.toList()

                            val matchingElse = elseBlocks.filter { elseStart -> ranges.none { elseStart in it } }
                            when {
                                matchingElse.size > 1 -> throw IllegalArgumentException("Multiple {{else}} blocks")
                                matchingElse.isNotEmpty() -> {
                                    val keyword = if (wheel is IfWheel) "not" else "if"

                                    val ifText = innerString.substring(0, matchingElse[0])

                                    val elseText =
                                        "{{#$keyword ${arguments["value"]}}}${innerString.substring(
                                            innerString.indexOf("}}", matchingElse[0]) + 2
                                        )}\n{{/$keyword}}"
                                    // {{else}} actually exists
                                    parse(ifText) to (keyword to parse(elseText))
                                }
                                else -> parse(innerString) to null
                            }
                        } else parse(innerString) to null

                templateSkeletons.add(
                    BicycleWheelSkeleton(
                        engine,
                        wheel,
                        innerTemplate,
                        arguments,
                        WheelValueMap(setVariables)
                    )
                )

                elseTemplate?.let { (keyword, template) ->
                    templateSkeletons.add(
                        BicycleWheelSkeleton(
                            engine,
                            if (keyword == "if") IfWheel() else NotWheel(),
                            template,
                            arguments,
                            WheelValueMap(setVariables)
                        )
                    )
                }

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
        if (pivotalEqualsIndex == -1) return Pair(
            transformArguments(wheel, parseArguments(string), string),
            mutableMapOf()
        )

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

    fun parseWheel(string: String): Pair<Wheel, String> {
        var wheelDefinition = string
        val attributes = mutableListOf<WheelGrammar>()

        var hasGrammar = true

        while (hasGrammar) {
            val match = WheelGrammar.values().firstOrNull { wheelDefinition.startsWith(it.representation) }
            if (match != null) {
                attributes.add(match)
                wheelDefinition = wheelDefinition.removePrefix(match.representation).trimStart()
            } else hasGrammar = false
        }

        attributes.forEach { attribute ->
            attribute.replaceStart?.let { wheelDefinition = it + wheelDefinition }
            attribute.replaceAfter?.let { wheelDefinition += it }
        }

        val foundWheel = engine.allWheels.firstOrNull { wheelDefinition.startsWith(it.name) }?.apply {
            wheelDefinition = wheelDefinition.removePrefix(this.name).trim()
        } ?: VariableResolverWheel()

        return Pair(foundWheel, wheelDefinition)
    }
}

internal enum class WheelGrammar(val representation: String, val replaceStart: String?, val replaceAfter: String?) {
    RAW("&", null, " noescape=true"),
    SHOW_NULL("?", null, " show-null=true"),
    ALLOW_FUNCTION_INVOCATION("!!", null, " allow-function-invocation=true"),
    NOT("^", "#not ", null),
    TEMPLATE_RESOLVER(">", "template-resolver ", null),
    WHEEL_FUNCTION_START("#", null, null)
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