package com.github.tanukun.blockscreenshot.client

import com.github.tanukun.blockscreenshot.BlockScreenshot
import com.github.tanukun.blockscreenshot.BlockScreenshot.SCREENSHOT_PACKET_ID
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.util.ScreenshotRecorder
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.ClickEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.io.File

@Environment(EnvType.CLIENT)
object BlockScreenshotClient : ClientModInitializer {
    override fun onInitializeClient() {
        // スクショ保存フォルダ
        val minecraft = MinecraftClient.getInstance()
        val screenshotDir = File(minecraft.runDirectory, "block_screenshot")

        // パケットが来たらスクショを作成
        ClientPlayNetworking.registerGlobalReceiver(SCREENSHOT_PACKET_ID) { client, _, buf, _ ->
            // ブロックの名前
            val blockName = buf.readText()
            // フォルダを作成
            val blockDir = File(screenshotDir, blockName.string)
            blockDir.mkdirs()

            // execute内ではレンダリングスレッドで実行される
            client.execute {
                // スクリーンショットを取る
                val resultText = client.takeScreenshot(blockDir, 512, 512)
                // チャットに表示
                client.inGameHud.chatHud.addMessage(resultText)
            }
        }
    }

    /**
     * スクリーンショットを撮る
     * @param directory スクリーンショットを保存するフォルダ
     * @param width スクリーンショットの幅
     * @param height スクリーンショットの高さ
     * @return スクリーンショットを撮影したときのチャットメッセージ
     */
    private fun MinecraftClient.takeScreenshot(directory: File, width: Int, height: Int): Text? {
        val window = this.window
        val player = this.player
            ?: return null
        val framebufferWidth: Int = window.framebufferWidth
        val framebufferHeight: Int = window.framebufferHeight
        val framebuffer: Framebuffer = SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC)
        val pitch: Float = player.pitch
        val yaw: Float = player.yaw
        val prevPitch: Float = player.prevPitch
        val prevYaw: Float = player.prevYaw
        this.gameRenderer.setBlockOutlineEnabled(false)
        var resultText: MutableText?
        try {
            this.gameRenderer.isRenderingPanorama = true
            this.worldRenderer.reloadTransparencyShader()
            window.framebufferWidth = width
            window.framebufferHeight = height

            player.prevYaw = player.yaw
            player.prevPitch = player.pitch
            framebuffer.beginWrite(true)
            this.gameRenderer.renderWorld(1.0f, 0L, MatrixStack())
            try {
                Thread.sleep(10L)
            } catch (_: InterruptedException) {
            }
            ScreenshotRecorder.saveScreenshot(directory, framebuffer) {}

            val text: Text = Text.literal(directory.name).formatted(Formatting.UNDERLINE).styled { style: Style ->
                style.withClickEvent(
                    ClickEvent(ClickEvent.Action.OPEN_FILE, directory.absolutePath)
                )
            }
            resultText = Text.translatable("screenshot.success", *arrayOf<Any>(text))
            return resultText
        } catch (e: Exception) {
            BlockScreenshot.LOGGER.error("Couldn't save image", e)
            resultText = Text.translatable("screenshot.failure", *arrayOf<Any?>(e.message))
        } finally {
            player.pitch = pitch
            player.yaw = yaw
            player.prevPitch = prevPitch
            player.prevYaw = prevYaw
            this.gameRenderer.setBlockOutlineEnabled(true)
            window.framebufferWidth = framebufferWidth
            window.framebufferHeight = framebufferHeight
            framebuffer.delete()
            this.gameRenderer.isRenderingPanorama = false
            this.worldRenderer.reloadTransparencyShader()
            this.framebuffer.beginWrite(true)
        }

        return resultText
    }
}