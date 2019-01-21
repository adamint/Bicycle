package com.adamratzman.bicycle

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.time.ZoneOffset

class DateRenderer : WheelVariableBlock("date", listOf(WheelArgument("date", listOf(ParameterType.LONG), false))) {
    override fun render(arguments: WheelValueMap, setVariables: WheelValueMap, context: BicycleContext): Any {
        return Instant.ofEpochMilli(arguments["date"] as Long).let { instant ->
            (setVariables["offset"] as? Int)?.let { instant.atOffset(ZoneOffset.ofHours(it)) } ?: instant
        }.toString()
    }
}

data class TestClazz(val data: String) {
    fun transform() = data.toUpperCase()
    fun transform(plus: String) = data + plus
}

class BicycleTemplateTest : Spek({
    describe("Template tests") {
        val bicycle = BicycleEngine()
        it("Render") {
            var t = System.currentTimeMillis()
            bicycle.compileResourcesDirectory("templates")

            val template = bicycle.templates["index.bike"]!!

            val model = mapOf(
                "condition" to true,
                "name" to "Adam",
                "condition2" to true,
                "model" to (1..5).map { TestClazz("test with string:  number $it") }
            )

            println(
                template.render(model)
            )
        }


        it("Valid") {
            val engine = BicycleEngine()
            val basicTextTemplate = BicycleTemplate(
                engine,
                listOf(
                    BicycleTextSkeleton("<p>Test</p>")
                )
            )


            BicycleTemplate(
                engine,
                listOf(
                    BicycleTextSkeleton("test"),
                    BicycleWheelSkeleton(
                        engine,
                        IfWheel(),
                        basicTextTemplate,
                        listOf(Pair("value", true)),
                        WheelValueMap()
                    ),
                    BicycleWheelSkeleton(
                        engine,
                        DateRenderer(), null,
                        listOf(null to System.currentTimeMillis()),
                        WheelValueMap(mapOf("offset" to -3))
                    ),
                    BicycleWheelSkeleton(
                        engine,
                        VariableResolverWheel(), null,
                        listOf(null to "test.transform(test2)"),
                        WheelValueMap(mapOf("allow-function-invocation" to true))
                    )
                )
            ).render(BicycleContext(WheelValueMap(mapOf("test2" to "4", "test" to TestClazz("hello world")))))
                .let { println(it) }
        }
    }
})