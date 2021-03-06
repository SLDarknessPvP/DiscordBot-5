/*
 * Copyright 2017 github.com/kaaz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package emily.command.administrative;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import emily.command.CommandReactionListener;
import emily.command.CommandVisibility;
import emily.command.ICommandReactionListener;
import emily.command.PaginationInfo;
import emily.core.AbstractCommand;
import emily.guildsettings.DefaultGuildSettings;
import emily.guildsettings.GSetting;
import emily.handler.GuildSettings;
import emily.handler.Template;
import emily.main.BotConfig;
import emily.main.DiscordBot;
import emily.permission.SimpleRank;
import emily.util.DisUtil;
import emily.util.Emojibet;
import emily.util.Misc;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.utils.PermissionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * !config
 * gets/sets the configuration of the bot
 */
public class ConfigCommand extends AbstractCommand implements ICommandReactionListener<PaginationInfo> {
    public static final int CFG_PER_PAGE = 15;

    public ConfigCommand() {
        super();
    }

    private static MessageEmbed makeEmbedConfig(Guild guild, int activePage) {
        EmbedBuilder b = new EmbedBuilder();
        List<String> keys = DefaultGuildSettings.getWritableKeys();
        Collections.sort(keys);
        int maxPage = (int) Math.ceil((double) keys.size() / (double) CFG_PER_PAGE);
        activePage = Math.max(0, Math.min(maxPage - 1, activePage - 1));
        int endIndex = activePage * CFG_PER_PAGE + CFG_PER_PAGE;
        int elements = 0;
        for (int i = activePage * CFG_PER_PAGE; i < keys.size() && i < endIndex; i++) {
            String key = keys.get(i);
            b.addField(key.toLowerCase(), GuildSettings.get(guild.getId()).getDisplayValue(guild, key), true);
            elements++;
        }
        if (elements % 3 == 2) {
            b.addBlankField(true);
        }
        String commandPrefix = DisUtil.getCommandPrefix(guild);
        b.setFooter("Page " + (activePage + 1) + " / " + maxPage + " | Press the buttons for other pages", null);
        b.setDescription(String.format("To see more details about a setting:" + BotConfig.EOL +
                "`%1$scfg settingname`" + BotConfig.EOL + BotConfig.EOL, commandPrefix));
        b.setTitle("Current Settings for " + guild.getName() + " [" + (1 + activePage) + " / " + maxPage + "]", null);
        return b.build();
    }

    @Override
    public String getDescription() {
        return "Gets/sets the configuration of the bot";
    }

    @Override
    public String getCommand() {
        return "config";
    }

    @Override
    public String[] getUsage() {
        return new String[]{
                "config                    //overview",
                "config page <number>      //show page <number>",
                "config tags               //see what tags exist",
                "config tag <tagname>      //show settings with tagname",
                "config <property>         //check details of property",
                "config <property> <value> //sets property",
                "",
                "config reset yesimsure    //resets the configuration to the default settings",
        };
    }

    @Override
    public String[] getAliases() {
        return new String[]{
                "setting", "cfg"
        };
    }

    @Override
    public CommandVisibility getVisibility() {
        return CommandVisibility.PUBLIC;
    }

    @Override
    public String execute(DiscordBot bot, String[] args, MessageChannel channel, User author) {
        Guild guild;
        SimpleRank rank = bot.security.getSimpleRank(author, channel);
        if (rank.isAtLeast(SimpleRank.BOT_ADMIN) && args.length >= 1 && DisUtil.matchesGuildSearch(args[0])) {
            guild = DisUtil.findGuildBy(args[0], bot.getContainer());
            if (guild == null) {
                return Template.get("command_config_cant_find_guild");
            }
            args = Arrays.copyOfRange(args, 1, args.length);
        } else {
            guild = ((TextChannel) channel).getGuild();
        }

        if (!rank.isAtLeast(SimpleRank.GUILD_ADMIN)) {
            return Template.get("command_config_no_permission");
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("yesimsure")) {
                GuildSettings.get(guild).reset();
                return Template.get(channel, "command_config_reset_success");
            }
            return Template.get(channel, "command_config_reset_warning");
        }
        String tag = null;
        if (args.length > 0) {
            if (args[0].equals("tags")) {
                return "The following tags exist for settings: " + BotConfig.EOL + BotConfig.EOL +
                        Joiner.on(", ").join(DefaultGuildSettings.getAllTags()) + BotConfig.EOL + BotConfig.EOL +
                        "`" + DisUtil.getCommandPrefix(channel) + "cfg tag tagname` to see settings with tagname";
            }
            if (args[0].equals("tag") && args.length > 1) {
                tag = args[1].toLowerCase();
            }
        }
        if (args.length == 0 || tag != null || args.length > 0 && args[0].equals("page")) {
            String[] settings = GuildSettings.get(guild).getSettings();
            ArrayList<String> keys = new ArrayList<>(DefaultGuildSettings.getAllKeys());
            Collections.sort(keys);
            int activePage = 0;
            int maxPage = 1 + DefaultGuildSettings.countSettings(false) / CFG_PER_PAGE;
            if (PermissionUtil.checkPermission((TextChannel) channel, ((TextChannel) channel).getGuild().getSelfMember(), Permission.MESSAGE_EMBED_LINKS)) {
                if (args.length > 1 && args[0].equals("page")) {
                    activePage = Math.max(0, Math.min(maxPage - 1, Misc.parseInt(args[1], 0) - 1));
                }
                bot.queue.add(channel.sendMessage(makeEmbedConfig(guild, activePage)),
                        message ->
                                bot.commandReactionHandler.addReactionListener(((TextChannel) channel).getGuild().getId(), message,
                                        getReactionListener(author.getId(), new PaginationInfo(1, maxPage, guild))));

                return "";
            }

            String ret = "Current Settings for " + guild.getName() + BotConfig.EOL + BotConfig.EOL;
            if (tag != null) {
                ret += "Only showing settings with the tag `" + tag + "`" + BotConfig.EOL;
            }
            ret += ":information_source: Settings indicated with a `*` are different from the default value" + BotConfig.EOL + BotConfig.EOL;
            String cfgFormat = "`\u200B%-24s:`  %s" + BotConfig.EOL;
            boolean isEmpty = true;
            for (int i = activePage * CFG_PER_PAGE; i < keys.size() && i < activePage * CFG_PER_PAGE + CFG_PER_PAGE; i++) {
                String key = keys.get(i);
                GSetting gSetting = GSetting.valueOf(key);
                if (DefaultGuildSettings.get(key).isInternal()) {
                    if (!rank.isAtLeast(SimpleRank.BOT_ADMIN)) {
                        continue;
                    }
                }
                if (tag != null && !DefaultGuildSettings.get(key).hasTag(tag)) {
                    continue;
                }
                String indicator = "  ";
                if (rank.isAtLeast(SimpleRank.BOT_ADMIN) && DefaultGuildSettings.get(key).isInternal()) {
                    indicator = "r ";
                } else if (!settings[gSetting.ordinal()].equals(DefaultGuildSettings.getDefault(key))) {
                    indicator = "* ";
                }
                ret += String.format(cfgFormat, indicator + key, GuildSettings.get(guild.getId()).getDisplayValue(guild, key));
                isEmpty = false;
            }
            if (isEmpty && tag != null) {
                return "No settings found matching the tag `" + tag + "`";
            }

            return ret;
        }


        if (!DefaultGuildSettings.isValidKey(args[0])) {
            return Template.get("command_config_key_not_exists");
        }
        if (DefaultGuildSettings.get(args[0]).isInternal() && !rank.isAtLeast(SimpleRank.BOT_ADMIN)) {
            return Template.get("command_config_key_read_only");
        }

        if (args.length >= 2) {
            String newValue = args[1];
            for (int i = 2; i < args.length; i++) {
                newValue += " " + args[i];
            }
            if (newValue.length() > 64) {
                newValue = newValue.substring(0, 64);
            }
            if (args[0].equals("bot_listen") && args[1].equals("mine")) {
                bot.queue.add(channel.sendMessage(Emojibet.WARNING + " I will only listen to the configured `bot_channel`. If you rename the channel, you might not be able to access me anymore. " +
                        "You can reset by typing `@" + channel.getJDA().getSelfUser().getName() + " reset yesimsure`"));
            }

            if (GuildSettings.get(guild).set(guild, args[0], newValue)) {
                return Template.get("command_config_key_modified");
            }
        }

        String tblContent = "";
        GuildSettings setting = GuildSettings.get(guild);
        tblContent += setting.getDescription(args[0]);
        return "Config help for **" + args[0] + "**" + BotConfig.EOL + BotConfig.EOL +
                "Current value: \"**" + GuildSettings.get(guild.getId()).getDisplayValue(guild, args[0]) + "**\"" + BotConfig.EOL +
                "Default value: \"**" + setting.getDefaultValue(args[0]) + "**\"" + BotConfig.EOL + BotConfig.EOL +
                "Description: " + BotConfig.EOL +
                Misc.makeTable(tblContent) +
                "To set it back to default: `" + DisUtil.getCommandPrefix(channel) + "cfg " + args[0] + " " + setting.getDefaultValue(args[0]) + "`";
    }

    @Override
    public CommandReactionListener<PaginationInfo> getReactionListener(String userId, PaginationInfo data) {

        CommandReactionListener<PaginationInfo> listener = new CommandReactionListener<>(userId, data);
        listener.setExpiresIn(TimeUnit.MINUTES, 2);
        listener.registerReaction(Emojibet.PREV_TRACK, o -> {
            if (listener.getData().previousPage()) {
                o.editMessage(new MessageBuilder().setEmbed(makeEmbedConfig(data.getGuild(), listener.getData().getCurrentPage())).build()).complete();
            }
        });
        listener.registerReaction(Emojibet.NEXT_TRACK, o -> {
            if (listener.getData().nextPage()) {
                o.editMessage(new MessageBuilder().setEmbed(makeEmbedConfig(data.getGuild(), listener.getData().getCurrentPage())).build()).complete();
            }
        });
        return listener;
    }
}