package com.witherflare.partyfinderplus;

import com.google.gson.*;
import com.witherflare.partyfinderplus.commands.PartyFinderPlusCommand;
import com.witherflare.partyfinderplus.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Mod(
    modid = PartyFinderPlus.MOD_ID,
    name = PartyFinderPlus.MOD_NAME,
    version = PartyFinderPlus.VERSION
)
public class PartyFinderPlus {

    /* When updating mod version, change these files:
    mcmod.info
    build.gradle
    PartyFinderPlus.java
     */

    public static final String MOD_ID = "partyfinderplus";
    public static final String MOD_NAME = "PartyFinderPlus";
    public static final String VERSION = "0.2.1";
    public static final String configLocation = "./config/partyfinderplus.toml";

    public static final Logger logger = LogManager.getLogger();
    public static Config config;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        config = new Config();
        config.preload();

        MinecraftForge.EVENT_BUS.register(this);

        new PartyFinderPlusCommand().register();
    }

    void chat (String message) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.thePlayer.addChatMessage(new ChatComponentText(message));
    }
    void say (String message) {
        Minecraft.getMinecraft().thePlayer.sendChatMessage(message);
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) throws IOException {
        int KICKDELAY = 500;

        String PREFIX = "??dPartyFinderPlus ??r>??e";
        String msg = event.message.getUnformattedText();
        String formattedMsg = event.message.getFormattedText();

        if (msg.contains("joined the dungeon group! ") && !msg.contains(":")) {
            if (!config.toggled) return;
            if (!config.autoKickToggled) return;


            new Thread(() -> {
                boolean sentFeedback = false;
                if (config.apiKey.length() < 1) {
                    if (!sentFeedback) {
                        chat(PREFIX + " ??cYour API key is not set! Make sure you set your API key in /pfp.");
                        sentFeedback = true;
                    }
                }
                // Get the user and their class
                String user = msg.split("Dungeon Finder > ")[1];
                String userClass = user.split(" joined the dungeon group! ")[1];
                userClass = userClass.split(" Level ")[0];
                userClass = userClass.replaceAll("[()]", "");
                user = user.split(" joined the dungeon group! ")[0];

                HttpClient client = HttpClients.createDefault();
                JsonParser parser = new JsonParser();

                HttpResponse res = null;
                try {
                    res = client.execute(new HttpGet("https://api.mojang.com/users/profiles/minecraft/" + user));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String body = null;
                try {
                    body = EntityUtils.toString(res.getEntity());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                JsonObject data = parser.parse(body).getAsJsonObject();

                String uuid = data.get("id").getAsString();

                HttpResponse res1 = null;
                try {
                    res1 = client.execute(new HttpGet("https://api.hypixel.net/skyblock/profiles?key=" + config.apiKey + "&uuid=" + uuid));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String body1 = null;
                try {
                    body1 = EntityUtils.toString(res1.getEntity());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                JsonObject hypixelProfileData = parser.parse(body1).getAsJsonObject();

                JsonArray profiles = null;
                try {
                    profiles = hypixelProfileData.get("profiles").getAsJsonArray();
                } catch (NullPointerException e) {
                    System.out.println(e.getMessage());
                    if (!sentFeedback) {chat(PREFIX + " ??cAn error occured while grabbing the profile data of ??e" + user + "??c. Make sure your API key is valid and try again.");sentFeedback = true;}
                }

                String currentProfileId = null;

                try {
                    for (JsonElement profile : profiles) {
                        if (profile.getAsJsonObject().get("members").getAsJsonObject().get(uuid).getAsJsonObject().get("selected").getAsBoolean()) {
                            currentProfileId = profile.getAsJsonObject().get("profile_id").getAsString();
                        }
                    }


                    HttpResponse res2 = null;
                    res2 = client.execute(new HttpGet("https://api.hypixel.net/player?key=" + config.apiKey + "&uuid=" + uuid));
                    String body2 = null;
                    body2 = EntityUtils.toString(res2.getEntity());


                    JsonObject generalData = parser.parse(body2).getAsJsonObject();
                    int secretCount = generalData.get("player").getAsJsonObject().get("achievements").getAsJsonObject().get("skyblock_treasure_hunter").getAsInt();

                    if (config.extraInfo) {chat(PREFIX + " ??7" + user + "??e has ??b" + secretCount + "??e secrets.");}

                    int requiredSecrets = config.secretMin;
//                    if (config.secretMin == 1) {requiredSecrets=1000;}
//                    if (config.secretMin == 2) {requiredSecrets=2500;}
//                    if (config.secretMin == 3) {requiredSecrets=5000;}
//                    if (config.secretMin == 4) {requiredSecrets=7500;}
//                    if (config.secretMin == 5) {requiredSecrets=10000;}
//                    if (config.secretMin == 6) {requiredSecrets=12500;}
//                    if (config.secretMin == 7) {requiredSecrets=15000;}
//                    if (config.secretMin == 8) {requiredSecrets=20000;}

                    if (userClass.contains("Healer") && !config.healerAllowed) {
                        chat(PREFIX + " ??c<!> ??eThis user is playing a class you have not allowed to join! Kicking user.");
                        if (config.autoKickReason) say("/pc " + user + " kicked for: Disallowed Class");
                        Thread.sleep(KICKDELAY);
                        say("/p kick " + user);
                    } else if (userClass.contains("Mage") && !config.mageAllowed) {
                        chat(PREFIX + " ??c<!> ??eThis user is playing a class you have not allowed to join! Kicking user.");
                        if (config.autoKickReason) say("/pc " + user + " kicked for: Disallowed Class");
                        Thread.sleep(KICKDELAY);
                        say("/p kick " + user);
                    } else if (userClass.contains("Berserk") && !config.berserkAllowed) {
                        chat(PREFIX + " ??c<!> ??eThis user is playing a class you have not allowed to join! Kicking user.");
                        if (config.autoKickReason) say("/pc " + user + " kicked for: Disallowed Class");
                        Thread.sleep(KICKDELAY);
                        say("/p kick " + user);
                    } else if (userClass.contains("Archer") && !config.archerAllowed) {
                        chat(PREFIX + " ??c<!> ??eThis user is playing a class you have not allowed to join! Kicking user.");
                        if (config.autoKickReason) say("/pc " + user + " kicked for: Disallowed Class");
                        Thread.sleep(KICKDELAY);
                        say("/p kick " + user);
                    } else if (userClass.contains("Tank") && !config.tankAllowed) {
                        chat(PREFIX + " ??c<!> ??eThis user is playing a class you have not allowed to join! Kicking user.");
                        if (config.autoKickReason) say("/pc " + user + " kicked for: Disallowed Class");
                        Thread.sleep(KICKDELAY);
                        say("/p kick " + user);
                    } else if (requiredSecrets >= secretCount) {
                        chat(PREFIX + " ??c<!> ??eThis user does not have the required amount of secrets to join! ??7(" + secretCount + "/" + requiredSecrets + ")??e Kicking user.");
                        if (config.autoKickReason) say("/pc " + user + " kicked for: Low Secrets (" + secretCount + "/" + requiredSecrets + ")");
                        Thread.sleep(KICKDELAY);
                        say("/p kick " + user);
                    } else {
                        // The user made it past the first checks, hooray!
                        HttpResponse res3 = null;
                        res3 = client.execute(new HttpGet("https://api.hypixel.net/skyblock/profile?key=" + config.apiKey + "&uuid=" + uuid + "&profile=" + currentProfileId));
                        String body3 = null;
                        body3 = EntityUtils.toString(res3.getEntity());
                        JsonObject inventoryData = parser.parse(body3).getAsJsonObject();

                        Set<String> keys = new HashSet<>();
                        for (Map.Entry<String, JsonElement> entry : inventoryData.get("profile").getAsJsonObject().get("members").getAsJsonObject().entrySet()) {
                            keys.add(entry.getKey());
                        }

                        String invContents = null;
                        try {
                            for (String x : keys) {
                                invContents = inventoryData.get("profile").getAsJsonObject().get("members").getAsJsonObject().get(uuid).getAsJsonObject().get("inv_contents").getAsJsonObject().get("data").getAsString();
                            }
                        } catch (NullPointerException e) {
                            if (!sentFeedback) {chat(PREFIX + " ??cThis user does not have their API on!");sentFeedback = true;}
                        }

                        byte[] bytearray = Base64.getDecoder().decode(invContents);
                        ByteArrayInputStream inputstream = new ByteArrayInputStream(bytearray);
                        NBTTagCompound nbt = CompressedStreamTools.readCompressed(inputstream);
                        NBTTagList itemTagList = nbt.getTagList("i", 10);
                        String invItems = itemTagList.toString();

                        if (!invItems.contains(":\"WITHER_SHIELD_SCROLL\"") && config.needsNecronBlade) {
                            chat(PREFIX + " ??c<!> ??eThis user does not have a Necron Blade! Kicking user.");
                            if (config.autoKickReason) say("/pc " + user + " kicked for: No Necron Blade");
                            Thread.sleep(KICKDELAY);
                            say("/p kick " + user);
                        }
                        if (!invItems.contains("id:\"TERMINATOR\"") && config.needsTerm) {
                            chat(PREFIX + " ??c<!> ??eThis user does not have a Terminator! Kicking user.");
                            if (config.autoKickReason) say("/pc " + user + " kicked for: No Terminator");
                            Thread.sleep(KICKDELAY);
                            say("/p kick " + user);
                        }
                        if (!invItems.contains("id:\"TERMINATOR\"") && config.archTerm && userClass == "Archer") {
                            chat(PREFIX + " ??c<!> ??eThis user does not have a Terminator! Kicking user.");
                            if (config.autoKickReason) say("/pc " + user + " kicked for: No Terminator");
                            Thread.sleep(KICKDELAY);
                            say("/p kick " + user);
                        }
                    }
                } catch (Error | InterruptedException | IOException | NullPointerException e) {
                    if (!sentFeedback) {
                        chat(PREFIX + " ??cAn error has occured.");
                        sentFeedback = true;
                    }
                }
                // Dungeon Finder > [NAME] joined the dungeon group! ([CLASS] Level [CLASS LEVEL])
            }).start();
        }
    }
}
