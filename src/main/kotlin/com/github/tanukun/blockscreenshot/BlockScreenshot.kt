package com.github.tanukun.blockscreenshot

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.command.argument.Vec3ArgumentType
import net.minecraft.entity.Entity
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
        /**
         * 一度だけスクショを撮る
         * @param player 対象プレイヤー
         * @param center 中心座標
         */
        fun screenshotOnce(player: ServerPlayerEntity, center: Vec3d) {
            // ランダムにブロックを選択
            val randomBlock = Registry.BLOCK[Random.nextInt(Registry.BLOCK.size())]
            // スクショを撮る
            // ブロックを置く座標
            val centerBlockPos = BlockPos(center)
            val centerVec = Vec3d(centerBlockPos.x + 0.5, centerBlockPos.y + 0.5, centerBlockPos.z + 0.5)
            player.replaceBlock(centerBlockPos, randomBlock)
            player.teleportRandom(centerVec)
            // ちょっと後に実行
            timer.schedule(object : TimerTask() {
                override fun run() {
                    player.takeScreenshot(randomBlock)
                }
            }, 50)
        }

        /**
         * タスクのON/OFFを切り替える
         * @param player 対象プレイヤー
         * @param taskGenerator タスク生成関数
         */
        fun toggleTask(player: ServerPlayerEntity, taskGenerator: () -> TimerTask) {
            val taskToStop = playerTasks.remove(player)
            if (taskToStop != null) {
                // タスクがあればキャンセル
                taskToStop.cancel()
            } else {
                // タスクがなければ実行する
                val task = taskGenerator()
                // タスクを保存する
                playerTasks[player] = task
            }
        }

        /**
         * 連続でスクショを撮る
         * @param player 対象プレイヤー
         * @param center 中心座標
         * @return タスク
         */
        fun screenshotLoopTask(player: ServerPlayerEntity, center: Vec3d): TimerTask {
            // タスクを作成する
            val task = object : TimerTask() {
                /** すべてのブロックの種類数 */
                val size = Registry.BLOCK.size()

                /** 現在のブロックID */
                var index = 0

                override fun run() {
                    // 順番にブロックを選択
                    if (++index >= size) {
                        index = 0
                    }
                    val block = Registry.BLOCK[index]

                    // ブロックを置く座標
                    val centerBlockPos = BlockPos(center)
                    val centerVec = Vec3d(
                        centerBlockPos.x + 0.5, centerBlockPos.y + 0.5, centerBlockPos.z + 0.5
                    )
                    player.replaceBlock(centerBlockPos, block)
                    player.teleportRandom(centerVec)
                    // ちょっと後に実行
                    timer.schedule(object : TimerTask() {
                        override fun run() {
                            // スクショを撮る
                            player.takeScreenshot(block)
                        }
                    }, 50)
                }
            }

            // タスクを登録する
            timer.scheduleAtFixedRate(task, 0, 50)
            return task
        }

        /**
         * 指定回数ずつ連続でスクショを撮る
         * @param player 対象プレイヤー
         * @param center 中心座標
         * @param count 撮影回数
         * @return タスク
         */
        fun screenshotSequenceTask(player: ServerPlayerEntity, center: Vec3d, count: Int): TimerTask {
            // タスクを作成する
            val task = object : TimerTask() {
                /** すべてのブロックの種類数 */
                val size = Registry.BLOCK.size()

                /** タスクのコルーチン */
                val screenshotTask = sequence {
                    // ブロックを置く座標
                    val centerBlockPos = BlockPos(center)
                    val centerVec = Vec3d(
                        centerBlockPos.x + 0.5, centerBlockPos.y + 0.5, centerBlockPos.z + 0.5
                    )

                    // すべてのブロックを順番に置く
                    for (iIndex in 0 until size) {
                        // ブロックを置き換える
                        val block = Registry.BLOCK[iIndex]
                        yield(player.replaceBlock(centerBlockPos, block))
                        // スクショを指定枚数撮る
                        for (iCount in 0 until count) {
                            // プレイヤーをランダムな角度にTPする
                            yield(player.teleportRandom(centerVec))
                            // スクショを撮る
                            yield(player.takeScreenshot(block))
                        }
                    }
                }.iterator()

                override fun run() {
                    if (screenshotTask.hasNext()) {
                        // 次のスクショを撮る
                        screenshotTask.next()
                    } else {
                        // タスクをキャンセルする
                        cancel()
                    }
                }
            }

            // タスクを登録する
            timer.scheduleAtFixedRate(task, 0, 30)
            return task
        }

        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, _, _ ->
            // 単発スクショ
            dispatcher.register(
                CommandManager.literal("screenshot").then(CommandManager.argument("pos", Vec3ArgumentType.vec3(true))
                        .executes { context: CommandContext<ServerCommandSource> ->
                            // For versions below 1.19, replace "Text.literal" with "new LiteralText".
                            //context.getSource().sendMessage(Text.literal("Called /foo with no arguments"));
                            context.source.player?.let { player ->
                                // 中心座標
                                val center = Vec3ArgumentType.getVec3(context, "pos")
                                // 一回だけスクショ
                                screenshotOnce(player, center)
                            }
                            1
                        })
            )

            // 連続スクショ
            dispatcher.register(
                CommandManager.literal("screenshot_loop")
                    .then(CommandManager.argument("pos", Vec3ArgumentType.vec3(true))
                        .executes { context: CommandContext<ServerCommandSource> ->
                            // For versions below 1.19, replace "Text.literal" with "new LiteralText".
                            //context.getSource().sendMessage(Text.literal("Called /foo with no arguments"));
                            context.source.player?.let { player ->
                                // 中心座標
                                val center = Vec3ArgumentType.getVec3(context, "pos")
                                // 連続スクショ
                                toggleTask(player) { screenshotLoopTask(player, center) }
                            }
                            1
                        })
            )

            // 指定回数ずつ連続スクショ
            dispatcher.register(CommandManager.literal("screenshot_sequence")
                .then(CommandManager.argument("pos", Vec3ArgumentType.vec3(true))
                    .then(CommandManager.argument("count", IntegerArgumentType.integer())
                        .executes { context: CommandContext<ServerCommandSource> ->
                            // For versions below 1.19, replace "Text.literal" with "new LiteralText".
                            //context.getSource().sendMessage(Text.literal("Called /foo with no arguments"));
                            context.source.player?.let { player ->
                                // 中心座標
                                val center = Vec3ArgumentType.getVec3(context, "pos")
                                // スクショの数
                                val count = IntegerArgumentType.getInteger(context, "count")
                                // 連続スクショ
                                toggleTask(player) { screenshotSequenceTask(player, center, count) }
                            }
                            1
                        })))
        })
    }

    /**
     * ブロックを中心にランダムな角度にテレポートする
     * @param centerVec 中心座標
     */
    private fun ServerPlayerEntity.teleportRandom(
        centerVec: Vec3d
    ) {
        // ランダムな向き
        val yaw = Random.nextDouble() * 360.0
        val pitch = (Random.nextDouble() - 0.5f) * 180.0
        // 今の座標
        val xz = cos(Math.toRadians(pitch))
        val vec = Vec3d(
            -xz * sin(Math.toRadians(yaw)), -sin(Math.toRadians(pitch)), xz * cos(Math.toRadians(yaw))
        )
        // プレイヤーをテレポート
        teleport(
            getWorld(),
            centerVec.x + vec.x * 1.5,
            centerVec.y - getEyeHeight(EntityPose.STANDING) + vec.y * 1.5,
            centerVec.z + vec.z * 1.5,
            yaw.toFloat(),
            pitch.toFloat()
        )
        // 中心を見る
        lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, centerVec)
    }

    /**
     * ブロックを置き換える
     * @param centerBlockPos 置き換える座標
     * @param block 置き換えるブロック
     */
    private fun Entity.replaceBlock(
        centerBlockPos: BlockPos, block: Block
    ) {
        // ワールド
        val world = getWorld()
        // ランダムなブロックを設置
        world.setBlockState(centerBlockPos, block.defaultState)
    }

    /**
     * スクショを撮る
     * @param block ブロック
     */
    private fun ServerPlayerEntity.takeScreenshot(block: Block) {
        // パケット作成
        val buf = PacketByteBufs.create()
        buf.writeText(block.name)
        // スクショを撮る
        ServerPlayNetworking.send(this, SCREENSHOT_PACKET_ID, buf)
    }
}