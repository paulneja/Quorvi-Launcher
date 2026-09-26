package com.paulneja.quorvi.agent

import com.paulneja.quorvi.agent.engine.Classifier
import com.paulneja.quorvi.agent.engine.GoalKind
import com.paulneja.quorvi.agent.engine.PlanParser
import com.paulneja.quorvi.agent.engine.RunSpec
import com.paulneja.quorvi.agent.policy.HostResolver
import com.paulneja.quorvi.agent.policy.PolicyEngine
import com.paulneja.quorvi.agent.policy.Verdict
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanningTest {
    @Test
    fun planIsOrderedByDependencies() {
        val steps = PlanParser.parse("Sure! {\"steps\":[{\"id\":\"b\",\"title\":\"B\",\"dependsOn\":[\"a\"]},{\"id\":\"a\",\"title\":\"A\"},{\"id\":\"c\",\"title\":\"C\",\"dependsOn\":[\"a\",\"zzz\",\"c\"]}]}")!!
        assertEquals(listOf("a", "b", "c"), steps.map { it.id })
        assertEquals(listOf("a"), steps.last().dependsOn)
    }

    @Test
    fun cyclesAndGarbageAreRejected() {
        assertNull(PlanParser.parse("{\"steps\":[{\"id\":\"a\",\"title\":\"A\",\"dependsOn\":[\"b\"]},{\"id\":\"b\",\"title\":\"B\",\"dependsOn\":[\"a\"]}]}"))
        assertNull(PlanParser.parse("no json here"))
        assertNull(PlanParser.parse("{\"steps\":[]}"))
    }

    @Test
    fun goalsAreClassified() {
        assertEquals(GoalKind.DOCUMENT, Classifier.classify(RunSpec("Necesito un documento completo sobre fotosíntesis y dejalo en Descargas")).kind)
        assertEquals(GoalKind.CI_DIAGNOSIS, Classifier.classify(RunSpec("investigá por qué falló el workflow")).kind)
        assertEquals(GoalKind.CI_DIAGNOSIS, Classifier.classify(RunSpec("mirá esto", ciExecutionId = "github:a/b:1")).kind)
        assertEquals(GoalKind.GENERAL, Classifier.classify(RunSpec("contame un chiste")).kind)
    }

    private val policy = PolicyEngine(resolver = HostResolver { host ->
        when (host) {
            "router.example" -> listOf(InetAddress.getByName("192.168.1.1"))
            "meta.example" -> listOf(InetAddress.getByName("169.254.169.254"))
            else -> listOf(InetAddress.getByName("93.184.216.34"))
        }
    })

    @Test
    fun shellPolicyBlocksPublishingAndEscalation() {
        assertEquals(Verdict.DENY, policy.shell(listOf("git", "push")).verdict)
        assertEquals(Verdict.DENY, policy.shell(listOf("bash", "-lc", "cd repo && git push -f origin main")).verdict)
        assertEquals(Verdict.DENY, policy.shell(listOf("gh", "pr", "create", "--fill")).verdict)
        assertEquals(Verdict.DENY, policy.shell(listOf("su", "-c", "id")).verdict)
        assertEquals(Verdict.DENY, policy.shell(listOf("rm", "-rf", "/sdcard")).verdict)
        assertEquals(Verdict.ALLOW, policy.shell(listOf("git", "clone", "https://github.com/a/b")).verdict)
        assertEquals(Verdict.ALLOW, policy.shell(listOf("python", "build.py")).verdict)
    }

    @Test
    fun networkPolicyBlocksLocalTargets() {
        assertEquals(Verdict.ALLOW, policy.network("https://es.wikipedia.org/wiki/Fotos%C3%ADntesis").verdict)
        assertEquals(Verdict.DENY, policy.network("http://router.example/admin").verdict)
        assertEquals(Verdict.DENY, policy.network("http://meta.example/latest").verdict)
        assertEquals(Verdict.DENY, policy.network("http://localhost:8080").verdict)
        assertEquals(Verdict.DENY, policy.network("file:///etc/passwd").verdict)
    }
}
