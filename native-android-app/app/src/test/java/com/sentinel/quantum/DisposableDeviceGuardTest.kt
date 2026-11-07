package com.sentinel.quantum

import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runner.JUnitCore
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

class DisposableDeviceGuardTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun realSamsungIsRefusedWithoutExplicitOptIn() {
        assertFalse(DisposableDevicePolicy.permits("samsung/e2sxeea/e2s:17/release-keys","exynos2400","e2sxeea",null))
    }
    @Test fun emulatorSignalsAreRecognized() {
        for ((fingerprint,hardware,product) in listOf(Triple("generic/sdk","other","other"),Triple("testEMULATOR","other","other"),Triple("other","goldfish","other"),Triple("other","ranchu","other"),Triple("other","other","sdk_gphone64_arm64"))) {
            assertTrue(DisposableDevicePolicy.permits(fingerprint,hardware,product,null))
        }
    }
    @Test fun optInMustBeExactAndDeliberate() {
        assertTrue(DisposableDevicePolicy.permits("samsung","exynos","e2s","true"))
        for (arg in listOf(null,"","false","TRUE","true ")) assertFalse(DisposableDevicePolicy.permits("samsung","exynos","e2s",arg))
    }
    @Test fun skipPrecedesBeforeBodyAndAfterAndIsNotPassOrIgnore() {
        Fixture.directory=folder.root;Fixture.allowed=false;Fixture.throwInGuard=false
        var skipped=0;val core=JUnitCore()
        core.addListener(object:RunListener(){override fun testAssumptionFailure(failure:Failure){skipped++;assertTrue(failure.message.contains("SKIP:"))}})
        val result=core.run(FixtureRunner())
        assertEquals(1,result.runCount);assertEquals(0,result.failureCount);assertEquals(0,result.ignoreCount);assertEquals(1,skipped)
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }
    @Test fun permittedFixtureActuallyRunsAllPhases() {
        Fixture.directory=folder.root;Fixture.allowed=true;Fixture.throwInGuard=false
        val result=JUnitCore().run(FixtureRunner())
        assertTrue(result.wasSuccessful());assertEquals(setOf("before","body","after"),folder.root.listFiles()!!.map{it.name}.toSet())
    }
    @Test fun guardFailureNeverRunsFixturesOrMasqueradesAsSkip() {
        Fixture.directory=folder.root;Fixture.throwInGuard=true;var skipped=0
        val core=JUnitCore();core.addListener(object:RunListener(){override fun testAssumptionFailure(failure:Failure){skipped++}})
        val result=core.run(FixtureRunner())
        assertEquals(1,result.failureCount);assertEquals(0,skipped);assertTrue(folder.root.listFiles()!!.isEmpty())
    }
    // No @Test on the fixture: Gradle must not discover/run this control case on its own.
    class FixtureRunner : BlockJUnit4ClassRunner(Fixture::class.java) {
        override fun computeTestMethods(): List<FrameworkMethod> = listOf(FrameworkMethod(Fixture::class.java.getMethod("body")))
    }
    class Fixture {
        @get:Rule val safety=DisposableDeviceRule { if(throwInGuard)throw SecurityException("metadata unavailable");allowed }
        @Before fun before(){File(directory,"before").writeText("write")}
        fun body(){File(directory,"body").writeText("write")}
        @After fun after(){File(directory,"after").writeText("write")}
        companion object {lateinit var directory:File;var allowed=false;var throwInGuard=false}
    }
}
