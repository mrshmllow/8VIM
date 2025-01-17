package inc.flide.vim8.ime.layout

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import arrow.core.None
import arrow.core.right
import inc.flide.vim8.VIM8Application
import inc.flide.vim8.arbitraries.Arbitraries
import inc.flide.vim8.ime.LayoutLoader
import inc.flide.vim8.ime.layout.models.KeyboardData
import inc.flide.vim8.ime.layout.models.error.ExceptionWrapperError
import inc.flide.vim8.ime.layout.models.info
import inc.flide.vim8.ime.layout.models.yaml.name
import inc.flide.vim8.vim8Application
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.arbitrary.next
import io.mockk.clearMocks
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import java.io.InputStream
import org.apache.commons.codec.digest.DigestUtils

class LayoutSpec : FunSpec({
    val context = mockk<Context>(relaxed = true)
    val resources = mockk<Resources>()
    val contentResolver = mockk<ContentResolver>()
    val cache = mockk<Cache>(relaxed = true)
    val inputStream = mockk<InputStream>(relaxed = true)
    val layoutLoader = mockk<LayoutLoader>()
    val application = mockk<VIM8Application>()

    beforeSpec {
        mockkStatic(DigestUtils::class)
        mockkStatic(::vim8Application)
        every { vim8Application() } returns application
        every { application.cache } returns cache
        every { DigestUtils.md5Hex(any<InputStream>()) } returns ""
        every { context.resources } returns resources
        every { context.contentResolver } returns contentResolver
        every { context.packageName } returns ""
    }

    beforeTest {
        every { cache.load(any()) } returns None
    }

    context("Embedded layout") {

        beforeTest {
            every { resources.getIdentifier(any(), any(), any()) } returns 0
        }

        context("loading InputStream") {
            test("the resource is found") {
                every { resources.openRawResource(any()) } returns inputStream
                EmbeddedLayout("test").inputStream(context) shouldBeRight inputStream
            }

            test("the resource is not found") {
                val exception = Exception("resource not found")
                every { resources.openRawResource(any()) } throws exception
                EmbeddedLayout("test").inputStream(context) shouldBeLeft ExceptionWrapperError(
                    exception
                )
            }
        }

        test("loadKeyboardData") {
            val layout = spyk(EmbeddedLayout("en"))
            every { layout.inputStream(any()) } returns inputStream.right()
            val keyboardData = Arbitraries.arbKeyboardData.next()
            every {
                layoutLoader.loadKeyboardData(any())
            } returns keyboardData.right()
            layout.loadKeyboardData(layoutLoader, context) shouldBeRight KeyboardData.info.name.set(
                keyboardData,
                "English"
            )
        }
    }

    context("Custom layout") {
        val uri = mockk<Uri>()
        afterTest { clearMocks(uri) }
        context("loading InputStream") {
            test("the resource is found") {
                every { contentResolver.openInputStream(any()) } returns inputStream
                CustomLayout(uri).inputStream(context) shouldBeRight inputStream
            }

            test("the resource is not found") {
                val exception = Exception("resource not found")
                every { contentResolver.openInputStream(any()) } throws exception
                CustomLayout(uri).inputStream(context) shouldBeLeft ExceptionWrapperError(
                    exception
                )
            }
        }

        context("loadKeyboardData") {
            val layout = spyk(CustomLayout(uri))
            every { layout.inputStream(any()) } returns inputStream.right()
            val keyboardData = Arbitraries.arbKeyboardData.next()
            every { layoutLoader.loadKeyboardData(any()) } returns keyboardData.right()

            test("scheme is a file") {
                every { uri.scheme } returns "file"
                every { uri.lastPathSegment } returns "file.yaml"
                layout.loadKeyboardData(
                    layoutLoader,
                    context
                ) shouldBeRight KeyboardData.info.name.set(
                    keyboardData,
                    "file.yaml"
                )
            }

            test("scheme is a content") {
                every { uri.scheme } returns "content"
                val cursor = mockk<Cursor>(relaxed = true)
                every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
                every { cursor.count } returns 1
                every { cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME) } returns 0
                every { cursor.getString(any()) } returns "content.yaml"
                every { layoutLoader.loadKeyboardData(any()) } returns keyboardData.right()
                layout.loadKeyboardData(
                    layoutLoader,
                    context
                ) shouldBeRight KeyboardData.info.name.set(
                    keyboardData,
                    "content.yaml"
                )
            }
        }
    }

    afterTest {
        clearMocks(resources, contentResolver, cache, layoutLoader)
    }

    afterSpec {
        clearStaticMockk(DigestUtils::class)
    }
})
