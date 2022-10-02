package com.github.tanukun.blockscreenshot.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.RegistrationEnvironment
import net.minecraft.server.command.ServerCommandSource

@Environment(EnvType.CLIENT)
object BlockScreenshotClient : ClientModInitializer {
    override fun onInitializeClient() {
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher: CommandDispatcher<ServerCommandSource?>, registryAccess: CommandRegistryAccess?, environment: RegistrationEnvironment? ->
            dispatcher.register(CommandManager.literal("screenshot")
                .executes { context: CommandContext<ServerCommandSource> ->
                    // For versions below 1.19, replace "Text.literal" with "new LiteralText".
                    //context.getSource().sendMessage(Text.literal("Called /foo with no arguments"));
                    val player = context.source.player
                    player!!.teleport(player.getWorld(), player.x, player.y + 1, player.z, player.yaw, player.pitch)
                    1
                })
        })
    }
}