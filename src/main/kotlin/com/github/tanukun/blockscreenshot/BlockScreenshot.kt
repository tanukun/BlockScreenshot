package com.github.tanukun.blockscreenshot

import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.command.argument.Vec3ArgumentType
import net.minecraft.entity.EntityPose
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.nextInt

object BlockScreenshot : ModInitializer {
    /** ロガー */
    val LOGGER: Logger = LogManager.getLogger("BlockScreenshot")

    /** スクリーンショットのパケットID */
    val SCREENSHOT_PACKET_ID = Identifier.of("blockscreenshot", "take_screenshot")

    /** 遅延実行用 */
    private val timer = Timer()

    /** プレイヤーごとのタスク */
    private val playerTasks = mutableMapOf<ServerPlayerEntity, TimerTask>()

    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, _, _ ->
            // 単発スクショ
            dispatcher.register(
                CommandManager.literal("screenshot")
                    .then(
                        CommandManager.argument("pos", Vec3ArgumentType.vec3(true))
                            .executes { context: CommandContext<ServerCommandSource> ->
                                // For versions below 1.19, replace "Text.literal" with "new LiteralText".
                                //context.getSource().sendMessage(Text.literal("Called /foo with no arguments"));
                                context.source.player?.let { player ->
                                    val center = Vec3ArgumentType.getVec3(context, "pos")
                                    replaceAndTakeScreenshot(player, center)
                                }
                                1
                            })
            )

            // 連続スクショ
            dispatcher.register(
                CommandManager.literal("screenshot_loop")
                    .then(
                        CommandManager.argument("pos", Vec3ArgumentType.vec3(true))
                            .executes { context: CommandContext<ServerCommandSource> ->
                                // For versions below 1.19, replace "Text.literal" with "new LiteralText".
                                //context.getSource().sendMessage(Text.literal("Called /foo with no arguments"));
                                context.source.player?.let { player ->
                                    val taskToStop = playerTasks.remove(player)
                                    if (taskToStop != null) {
                                        // タスクがあればキャンセル
                                        taskToStop.cancel()
                                    } else {
                                        // タスクがなければ実行する
                                        // 中心座標
                                        val center = Vec3ArgumentType.getVec3(context, "pos")
                                        // タスクを作成する
                                        val task = object : TimerTask() {
                                            override fun run() {
                                                replaceAndTakeScreenshot(player, center)
                                            }
                                        }
                                        // タスクを登録する
                                        timer.scheduleAtFixedRate(task, 0, 100)
                                        // タスクを保存する
                                        playerTasks[player] = task
                                    }
                                }
                                1
                            })
            )
        })
    }

    private fun replaceAndTakeScreenshot(player: ServerPlayerEntity, center: Vec3d) {
        // ブロックを置く座標
        val centerBlockPos = BlockPos(center)
        val centerVec =
            Vec3d(centerBlockPos.x + 0.5, centerBlockPos.y + 0.5, centerBlockPos.z + 0.5)

        // ワールド
        val world = player.getWorld()
        // ランダムにブロックを選択
        val randomBlock = Registry.BLOCK[Random.nextInt(Registry.BLOCK.size())]
        // ランダムなブロックを設置
        world.setBlockState(centerBlockPos, randomBlock.defaultState)
        // ランダムな向き
        val yaw = Random.nextDouble() * 360.0
        val pitch = (Random.nextDouble() - 0.5f) * 180.0
        // 今の座標
        val xz = cos(Math.toRadians(pitch))
        val vec = Vec3d(
            -xz * sin(Math.toRadians(yaw)),
            -sin(Math.toRadians(pitch)),
            xz * cos(Math.toRadians(yaw))
        )
        // プレイヤーをテレポート
        player.teleport(
            world,
            centerVec.x + vec.x * 1.5,
            centerVec.y - player.getEyeHeight(EntityPose.STANDING) + vec.y * 1.5,
            centerVec.z + vec.z * 1.5,
            yaw.toFloat(),
            pitch.toFloat()
        )
        // 中心を見る
        player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, centerVec)
        // パケット作成
        val buf = PacketByteBufs.create()
        buf.writeText(randomBlock.name)

        // ちょっと後に実行
        timer.schedule(object : TimerTask() {
            override fun run() {
                // スクショを撮る
                ServerPlayNetworking.send(player, SCREENSHOT_PACKET_ID, buf)
            }
        }, 50)
    }

    private fun teleportReplaceAndTakeScreenshot(context: CommandContext<ServerCommandSource>) {
        // For versions below 1.19, replace "Text.literal" with "new LiteralText".
        //context.getSource().sendMessage(Text.literal("Called /foo with no arguments"));
        context.source.player?.let { player ->
            // ワールド
            val world = player.getWorld()
            // ランダムな座標
            val randomX = player.blockX + Random.nextInt(-100..100)
            val randomZ = player.blockZ + Random.nextInt(-100..100)
            // XZ座標の地表を探す
            val randomPos = (world.topY downTo world.bottomY)
                .map { BlockPos(randomX, it, randomZ) }
                .first { !world.isAir(it) }
            // ランダムにブロックを選択
            val randomBlock = Registry.BLOCK[Random.nextInt(Registry.BLOCK.size())]
            // ランダムなブロックを設置
            world.setBlockState(randomPos, randomBlock.defaultState)
            // プレイヤーをテレポート
            player.teleport(
                world,
                randomPos.x + 0.5,
                randomPos.y + 0.5,
                randomPos.z + 0.5,
                player.yaw,
                player.pitch
            )
        }
    }
}