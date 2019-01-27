package com.adamratzman.bicycle

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.time.ZoneOffset

class DateRenderer : WheelVariableBlock("date", listOf(WheelArgument("date", listOf(ParameterType.LONG), false))) {
    override fun render(arguments: WheelValueMap, context: BicycleContext): Any {
        return Instant.ofEpochMilli(arguments["date"] as Long).let { instant ->
            (arguments["offset"] as? Int)?.let { instant.atOffset(ZoneOffset.ofHours(it)) } ?: instant
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
        bicycle.compileResourcesDirectory("templates")

        it("Wheel parsing") {
            val parser = BicycleTemplateParser(bicycle)
            println(
                parser.parseWheel("!! test-template.bike")
            )
        }

        it("Wheel definition parsing") {
            val parser = BicycleTemplateParser(bicycle)
            println(
                parser.parseWheelDefinition(
                    VariableResolverWheel(),
                    "variable show-null=true other=\"hello world\""
                )
            )
        }

        it("Argument parsing") {
            val parser = BicycleTemplateParser(bicycle)
            println(parser.parseArguments("true \"this is \\\" a string\" false 4.0 model"))
        }

        it("Assignment parsing") {
            val parser = BicycleTemplateParser(bicycle)
            println(parser.parseAssignments("test=4 string=\"Hello world\" a=model"))
        }

        it("Render") {
            val template = bicycle.templates["secondary/header.bike"]!!

            val map = mutableMapOf<String, Any?>()
            map["title"] = "Adam Ratzman | Home"
            map["page"] = "home"
            map["position-bottom"] = true

            // meta
            map["description"] = "Hi, I'm Adam. I'm a linguistics and programming enthusiast."

            println(template.render(map))

        }

        it("Render 2") {
            println(bicycle.templates.map { it.key })
            val template = bicycle.templates["index-test.hbs"]!!
            val map = mutableMapOf<String, Any?>()
            map["condition"] = false
            map["model"] = (1..5).map { TestClazz("data") }
            map["name"] = "A d a m"

            println(template.render(map))
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
                        mapOf("value" to true)
                    ),
                    BicycleWheelSkeleton(
                        engine,
                        DateRenderer(), null,
                        mapOf("value" to System.currentTimeMillis())
                    ),
                    BicycleWheelSkeleton(
                        engine,
                        VariableResolverWheel(), null,
                        mapOf("value" to "test.transform(test2)")
                    )
                )
            ).render(BicycleContext(WheelValueMap(mapOf("test2" to "4", "test" to TestClazz("hello world")))))
                .let { println(it) }
        }
    }
})