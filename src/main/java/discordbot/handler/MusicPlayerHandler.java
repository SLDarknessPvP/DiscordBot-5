package discordbot.handler;

import discordbot.db.WebDb;
import discordbot.db.model.OMusic;
import discordbot.main.Config;
import discordbot.main.DiscordBot;
import net.dv8tion.jda.audio.player.FilePlayer;
import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.Message;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.entities.VoiceChannel;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MusicPlayerHandler {
	private final static Map<Guild, MusicPlayerHandler> playerInstances = new ConcurrentHashMap<>();
	private final Guild guild;
	private final DiscordBot bot;
	private OMusic currentlyPlaying = new OMusic();
	private Message activeMsg = null;
	private long currentSongLength = 0;
	private long currentSongStartTimeInSeconds = 0;
	private Random rng;

	private MusicPlayerHandler(Guild guild, DiscordBot bot) {
		this.guild = guild;
		this.bot = bot;
		rng = new Random();
		playerInstances.put(guild, this);
	}

	public static MusicPlayerHandler getFor(Guild guild, DiscordBot bot) {
		if (playerInstances.containsKey(guild)) {
			return playerInstances.get(guild);
		} else {
			return new MusicPlayerHandler(guild, bot);
		}
	}

	public boolean isConnectedTo(VoiceChannel channel) {
		return channel.equals(guild.getAudioManager().getConnectedChannel());
	}

	public void connectTo(VoiceChannel channel) {
		guild.getAudioManager().openAudioConnection(channel);
	}

	public boolean isConnected() {
		return guild.getAudioManager().getConnectedChannel() == null;
	}

	public boolean leave() {
		if (isConnected()) {
			return false;
		}
		guild.getAudioManager().closeAudioConnection();
		return true;
	}

	public void clearPlayList() {
//		AudioPlayer.getAudioPlayerForGuild(guild).getPlaylist().clear();
	}

	public OMusic getCurrentlyPlaying() {
		return currentlyPlaying;
	}

	/**
	 * When did the currently playing song start?
	 *
	 * @return timestamp in seconds
	 */
	public long getCurrentSongStartTime() {
		return currentSongStartTimeInSeconds;
	}

	/**
	 * track duration of current song
	 *
	 * @return duration in seconds
	 */
	public long getCurrentSongLength() {
		return currentSongLength;
	}

	/**
	 * Skips currently playing song
	 */
	public void skipSong() {
//		clearMessage();
//		AudioPlayer ap = AudioPlayer.getAudioPlayerForGuild(guild);
//		ap.skip();
		currentlyPlaying = new OMusic();
		currentSongLength = 0;
		currentSongStartTimeInSeconds = 0;
//		if (ap.getPlaylistSize() == 0) {
//			playRandomSong();
//		}
	}

	/**
	 * retreives a random .mp3 file from the music directory
	 *
	 * @return filename
	 */
	private String getRandomSong() {
		ArrayList<String> potentialSongs = new ArrayList<>();
		try (ResultSet rs = WebDb.get().select(
				"SELECT filename, youtube_title, lastplaydate " +
						"FROM playlist " +
						"WHERE banned = 0 " +
						"ORDER BY lastplaydate ASC " +
						"LIMIT 50")) {
			while (rs.next()) {
				potentialSongs.add(rs.getString("filename"));
			}
			rs.getStatement().close();
		} catch (SQLException e) {
			e.printStackTrace();
			bot.out.sendErrorToMe(e, bot);
		}
		return potentialSongs.get(rng.nextInt(potentialSongs.size()));
	}

//	public void onTrackEnded(AudioPlayer.Track oldTrack, Optional<AudioPlayer.Track> nextTrack) {
//		clearMessage();
//		currentSongLength = 0;
//		currentlyPlaying = new OMusic();
//		if (!nextTrack.isPresent()) {
//			playRandomSong();
//		}
//	}

//	public void onTrackStarted(AudioPlayer.Track track) {
//		clearMessage();
//		Map<String, Object> metadata = track.getMetadata();
//		String msg = "Now playing unknown file :(";
//		if (metadata.containsKey("file")) {
//			if (metadata.get("file") instanceof File) {
//				File f = (File) metadata.get("file");
//				getMp3Details(f);
//				OMusic music = TMusic.findByFileName(f.getAbsolutePath());
//				if (music.id == 0) {
//					music = TMusic.findByFileName(f.getName());
//					if (music.id > 0) {
//						music.filename = f.getAbsolutePath();
//						TMusic.update(music);
//					}
//				}
//				currentlyPlaying = music;
//				currentSongStartTimeInSeconds = System.currentTimeMillis() / 1000;
//				music.lastplaydate = currentSongStartTimeInSeconds;
//				TMusic.update(music);
//				if (music.artist != null && music.title != null && !music.artist.trim().isEmpty() && !music.title.trim().isEmpty()) {
//					msg = ":notes: " + music.artist + " - " + music.title;
//				} else if (music.youtubeTitle != null && !music.youtubeTitle.isEmpty()) {
//					msg = ":notes: " + music.youtubeTitle + " ** need details about song! ** check out **current**";
//				} else {
//					msg = ":floppy_disk: :thinking: Something is wrong with this file `" + f.getName() + "`";
//				}
//			}
//		}
//		if (GuildSettings.get(guild).getOrDefault(SettingMusicChannelTitle.class).equals("true")) {
//			try {
//				bot.getMusicChannel(guild).changeTopic(msg);
//			} catch (RateLimitException | DiscordException e) {
//				e.printStackTrace();
//				bot.out.sendErrorToMe(e);
//			} catch (MissingPermissionsException e) {
//				bot.out.sendAsyncMessage(bot.getMusicChannel(guild), "I don't have permission to change the topic of this channel :(" + Config.EOL +
//						" I'm disabling `music_channel_title` option for now. " + Config.EOL + e.getMessage(), null);
//				GuildSettings.get(guild).set("music_channel_title", "false");
//			}
//		}
//		if (!GuildSettings.get(guild).getOrDefault(SettingMusicPlayingMessage.class).equals("off")) {
//			activeMsg = bot.out.sendAsyncMessage(bot.getMusicChannel(guild), msg, null);
//		}
//	}

	/**
	 * Deletes 'now playing' message if it exists
	 */
//	private void clearMessage() {
//		if (activeMsg != null && GuildSettings.get(guild).getOrDefault(SettingMusicPlayingMessage.class).equals("clear")) {
//			try {
//				activeMsg.delete();
//				activeMsg = null;
//			} catch (MissingPermissionsException | RateLimitException | DiscordException ignored) {
//			}
//		}
//	}

	/**
	 * Adds a random song from the music directory to the queue
	 *
	 * @return successfully started playing
	 */
	public boolean playRandomSong() {
		return addToQueue(getRandomSong());
	}

	public boolean addToQueue(String filename) {
		File f = new File(filename);
		if (!f.exists()) {//check in config directory
			f = new File(Config.MUSIC_DIRECTORY + filename);
			bot.out.sendErrorToMe(new Exception("nosongexception :("), "filename: ", f.getAbsolutePath(), "plz fix", "I want music", bot);
			return false;
		}
		try {
			guild.getAudioManager().setSendingHandler(new FilePlayer(f));
		} catch (IOException | UnsupportedAudioFileException e) {
			return false;
		}
//		try {
//			AudioPlayer.getAudioPlayerForGuild(guild).queue(makeTrack(f));
//			return true;
//		} catch (IOException | UnsupportedAudioFileException e) {
//			e.printStackTrace();
//
//		}
		return false;
	}

	public List<User> getUsersInVoiceChannel() {
		ArrayList<User> userList = new ArrayList<>();
		VoiceChannel currentChannel = guild.getAudioManager().getConnectedChannel();
		if (currentChannel != null) {
			List<User> connectedUsers = currentChannel.getUsers();
			userList.addAll(connectedUsers.stream().filter(user -> !user.isBot()).collect(Collectors.toList()));
		}
		return userList;
	}

	/**
	 * Clears existing message and stops playing music for guild
	 */

	public void stopMusic() {
//		clearMessage();
//		currentSongLength = 0;
//		currentlyPlaying = new OMusic();
	}

	public float getVolume() {
		return 1;
//		return AudioPlayer.getAudioPlayerForGuild(guild).getVolume();
	}

	public List<OMusic> getQueue() {
		ArrayList<OMusic> list = new ArrayList<>();
		return list;
	}

}
