package com.sentinel.quantum.security
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MmsOutgoingPreparationNativeTest : MmsOutgoingPreparationNativeCases()
